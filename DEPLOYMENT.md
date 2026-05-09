# Hify 部署架构

## 组件清单

```
                        ┌─────────────┐
                        │   用户浏览器   │
                        └──────┬──────┘
                               │ HTTPS
                               ▼
                        ┌─────────────┐
                        │   Ingress    │  Nginx Ingress Controller
                        │  (K8s 入口)  │  TLS 终止、路由分发
                        └──────┬──────┘
                               │
                   ┌───────────┴───────────┐
                   │                       │
                   ▼                       ▼
            / 和 /api/**             /ws/**
            ┌───────────┐         ┌───────────┐
            │   Hify     │         │   Hify     │
            │   Server   │◄────────┤   Server   │
            │   (后端)    │ SSE     │   (后端)    │
            │   Pod ×2   │---------│   Pod ×2   │
            └──┬──┬──┬───┘         └──┬──┬──┬───┘
               │  │  │                │  │  │
          ┌────┘  │  └────┐      ┌────┘  │  └────┐
          ▼       ▼       ▼      ▼       ▼       ▼
       ┌──────┐┌──────┐┌──────────────┐
       │ MySQL ││ Redis ││ PostgreSQL   │
       │ 8.0  ││ 7.x  ││ + pgvector   │
       └──────┘└──────┘└──────────────┘
```

## 为什么是这些组件

| 组件 | 职责 | 为什么选它 |
|------|------|-----------|
| **Nginx Ingress** | K8s 集群入口，TLS 终止，请求路由 | K8s 标准入口，50 人不需要额外 API 网关 |
| **Hify Server** | 全部业务逻辑（单体应用） | 模块化单体，一个镜像包含前后端 |
| **MySQL 8.0** | 业务数据存储（Agent、Conversation、Workflow、User 等） | 团队最熟悉的关系型数据库 |
| **Redis** | 会话缓存、对话上下文缓存、SSE 连接注册表 | 高频读写场景（对话上下文）减轻 MySQL 压力 |
| **PostgreSQL + pgvector** | 向量存储，RAG 检索 | pgvector 是最简单的向量库方案，不需要额外部署 Milvus/Qdrant |

### 为什么不用单独的前端 Nginx Pod

Hify 是内部平台，前后端打包在一起：
- 构建时 `npm run build` 产物放进 Spring Boot 的 `static/` 目录
- Spring Boot 直接托管静态文件，同时提供 API
- 一个 Deployment、一个 Service、一个镜像，运维最简

50 人场景下不需要 CDN、不需要前端独立扩缩容。

### 为什么 pgvector 而不是独立向量库

- Milvus / Qdrant / Weaviate 各需要一个独立的集群，运维成本高
- pgvector 是 PostgreSQL 扩展，复用现有的 PostgreSQL 实例
- 50 人 + 几万条文档，pgvector 性能完全够用
- 等真到百万级向量再考虑迁移

---

## 请求流转

### 1. 普通页面访问（前端）

```
浏览器 → GET / → Ingress → Hify Server → 返回 index.html + JS/CSS
```

Spring Boot 托管 `classpath:/static/` 下的前端构建产物，没有额外路由。

### 2. 普通 API 调用（CRUD）

```
浏览器 → POST /api/v1/agents → Ingress → Hify Server → MySQL → 返回 JSON
```

### 3. 对话（非流式）

```
浏览器 → POST /api/v1/conversations/{id}/chat
       → Ingress
       → Hify Server (Tomcat 线程接收)
       → 提交到 llmTaskExecutor (Tomcat 线程释放)
       → OkHttp 调用外部 LLM API
       → 返回完整响应 JSON
```

### 4. 对话（流式 SSE）

```
浏览器 → GET /api/v1/conversations/{id}/stream (EventSource)
       → Ingress
       → Hify Server (Tomcat 线程 return SseEmitter 后释放)
       → llmTaskExecutor 中 OkHttp 流式读取 LLM 响应
       → SseEmitter 逐块推送
       → Ingress (需要配置 SSE 不缓冲，见下方配置)
       → 浏览器 EventSource 接收
```

### 5. RAG 检索

```
Hify Server → 用户提问
            → Embedding 调用 (OkHttp → 外部 LLM API)
            → 拿到向量
            → SELECT ... ORDER BY embedding <=> $1 LIMIT 5
              (PostgreSQL + pgvector)
            → 返回相关文档片段
            → 拼入 LLM Prompt
```

### 6. 工作流执行

```
浏览器 → POST /api/v1/workflows/{id}/run
       → Ingress
       → Hify Server
       → llmTaskExecutor 中执行 WorkflowEngine
       → 逐节点执行（LLM 节点、条件节点、代码节点...）
       → 每个节点结果写入日志
       → 返回最终结果
```

---

## K8s 资源清单

### Namespace

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: hify
```

### Hify Server Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hify-server
  namespace: hify
spec:
  replicas: 2                         # 两个副本，滚动更新不中断
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1               # 最多停 1 个，保证始终有 1 个可用
      maxSurge: 1
  selector:
    matchLabels:
      app: hify-server
  template:
    metadata:
      labels:
        app: hify-server
    spec:
      containers:
        - name: hify-server
          image: hify-server:latest
          ports:
            - containerPort: 8080
          resources:
            requests:
              cpu: "500m"              # 日常请求
              memory: "512Mi"
            limits:
              cpu: "2"                 # LLM 调用期间 CPU 峰值
              memory: "1Gi"
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: DB_HOST
              valueFrom:
                secretKeyRef:
                  name: hify-secrets
                  key: db-host
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: hify-secrets
                  key: db-password
            - name: REDIS_HOST
              value: "hify-redis.hify.svc.cluster.local"
            - name: PGVECTOR_HOST
              value: "hify-pgvector.hify.svc.cluster.local"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
```

### Hify Server Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: hify-server
  namespace: hify
spec:
  selector:
    app: hify-server
  ports:
    - port: 80
      targetPort: 8080
```

### Ingress（含 SSE 配置）

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: hify-ingress
  namespace: hify
  annotations:
    # SSE 关键配置：禁用缓冲，否则 chunk 会被 Nginx 缓住一次性返回
    nginx.ingress.kubernetes.io/proxy-buffering: "off"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"
spec:
  tls:
    - hosts:
        - hify.internal.example.com
      secretName: hify-tls
  rules:
    - host: hify.internal.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: hify-server
                port:
                  number: 80
```

### MySQL StatefulSet

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: hify-mysql
  namespace: hify
spec:
  serviceName: hify-mysql
  replicas: 1                         # 50 人不需要主从
  selector:
    matchLabels:
      app: hify-mysql
  template:
    metadata:
      labels:
        app: hify-mysql
    spec:
      containers:
        - name: mysql
          image: mysql:8.0
          ports:
            - containerPort: 3306
          resources:
            requests:
              cpu: "500m"
              memory: "512Mi"
            limits:
              cpu: "1"
              memory: "1Gi"
          env:
            - name: MYSQL_DATABASE
              value: hify
            - name: MYSQL_ROOT_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: hify-secrets
                  key: db-password
          volumeMounts:
            - name: mysql-data
              mountPath: /var/lib/mysql
  volumeClaimTemplates:
    - metadata:
        name: mysql-data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 20Gi
```

### Redis Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hify-redis
  namespace: hify
spec:
  replicas: 1
  selector:
    matchLabels:
      app: hify-redis
  template:
    metadata:
      labels:
        app: hify-redis
    spec:
      containers:
        - name: redis
          image: redis:7-alpine
          ports:
            - containerPort: 6379
          resources:
            requests:
              cpu: "100m"
              memory: "128Mi"
            limits:
              cpu: "500m"
              memory: "256Mi"
          # 无持久化，纯缓存用途，重启即清空
```

### PostgreSQL + pgvector StatefulSet

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: hify-pgvector
  namespace: hify
spec:
  serviceName: hify-pgvector
  replicas: 1
  selector:
    matchLabels:
      app: hify-pgvector
  template:
    metadata:
      labels:
        app: hify-pgvector
    spec:
      containers:
        - name: postgres
          image: pgvector/pgvector:pg16
          ports:
            - containerPort: 5432
          resources:
            requests:
              cpu: "250m"
              memory: "256Mi"
            limits:
              cpu: "1"
              memory: "512Mi"
          env:
            - name: POSTGRES_DB
              value: hify_vector
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: hify-secrets
                  key: pgvector-password
          volumeMounts:
            - name: pgvector-data
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
    - metadata:
        name: pgvector-data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 10Gi
```

---

## 资源预估（50 人）

| 组件 | CPU Request | CPU Limit | Memory Request | Memory Limit | 存储 |
|------|-------------|-----------|----------------|--------------|------|
| Hify Server ×2 | 500m | 2 | 512Mi | 1Gi | — |
| MySQL | 500m | 1 | 512Mi | 1Gi | 20Gi |
| Redis | 100m | 500m | 128Mi | 256Mi | — |
| PostgreSQL+pgvector | 250m | 1 | 256Mi | 512Mi | 10Gi |
| **总计** | **1.85 核** | **6.5 核** | **1.9Gi** | **3.8Gi** | **30Gi** |

一台 4 核 8G 的节点就能跑全部组件，留有余量。

---

## 应用配置

```yaml
# application-prod.yml
server:
  port: 8080
  tomcat:
    threads:
      max: 200
    max-connections: 8192

spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:3306/hify?useSSL=true&serverTimezone=UTC
    username: root
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20           # 50 人 + 2 Pod，连接池不需要大
      minimum-idle: 5

  data:
    redis:
      host: ${REDIS_HOST}
      port: 6379

# pgvector 数据源（第二数据源）
hify:
  vector:
    url: jdbc:postgresql://${PGVECTOR_HOST}:5432/hify_vector
    username: postgres
    password: ${PGVECTOR_PASSWORD}

# LLM 线程池
llm:
  executor:
    core-pool-size: 10
    max-pool-size: 30
    queue-capacity: 100
```

---

## Dockerfile

```dockerfile
# 构建阶段：前端
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build                      # 产物在 /app/frontend/dist/

# 构建阶段：后端
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline -B
COPY src/ ./src/
# 把前端产物复制到 Spring Boot 静态资源目录
COPY --from=frontend-build /app/frontend/dist/ ./src/main/resources/static/
RUN mvn package -DskipTests -B

# 运行阶段
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend-build /app/target/hify-server.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

一条 `docker build` 同时构建前后端，最终镜像只包含 JRE + jar。

---

## 部署流程

```bash
# 1. 构建镜像
docker build -t hify-server:latest .

# 2. 推送到私有镜像仓库（如果是远程 K8s）
docker tag hify-server:latest registry.internal.example.com/hify-server:latest
docker push registry.internal.example.com/hify-server:latest

# 3. 首次部署：创建所有资源
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/mysql.yaml
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/pgvector.yaml
kubectl apply -f k8s/server.yaml        # Deployment + Service
kubectl apply -f k8s/ingress.yaml

# 4. 日常更新：只更新 Deployment（滚动更新，不中断服务）
kubectl set image deployment/hify-server hify-server=hify-server:v1.2.0 -n hify
```

---

## 数据库初始化

```sql
-- MySQL: 业务表（由 Flyway 管理）
-- 启动时自动执行 src/main/resources/db/migration/ 下的 SQL 文件

-- PostgreSQL: pgvector 扩展 + 向量表
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunk (
    id BIGSERIAL PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),             -- OpenAI text-embedding-ada-002 维度
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

-- 向量检索索引（IVFFlat，适合几万到几十万条）
CREATE INDEX idx_document_chunk_embedding
ON document_chunk USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);
```
