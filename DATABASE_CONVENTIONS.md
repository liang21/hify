# Hify 数据库性能规范

## 适用范围

- MySQL 8.x：业务数据（Agent、Conversation、Workflow、User、MCP Tool 等）
- PostgreSQL + pgvector：向量数据（document_chunk 表）
- MyBatis-Plus 为 ORM，SQL 写在 XML 或用 QueryWrapper

---

## 一、通用字段约定

### 每张表必须有的字段

```sql
id              BIGINT       NOT NULL    -- 主键，雪花算法生成（MyBatis-Plus ASSIGN_ID）
created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
```

### 字段命名和类型规则

| 规则 | 示例 | 说明 |
|------|------|------|
| 全部用 snake_case | `model_provider_id` | 不用 camelCase |
| 布尔值用 TINYINT(1) | `is_deleted TINYINT(1) NOT NULL DEFAULT 0` | MySQL 没有原生 boolean |
| 状态用 TINYINT | `status TINYINT NOT NULL DEFAULT 1` | 不用 ENUM（加值要 ALTER TABLE） |
| 金额/精确小数用 DECIMAL | `DECIMAL(10, 2)` | 不用 FLOAT/DOUBLE |
| 文本长度不固定用 TEXT | `system_prompt TEXT` | 不用 VARCHAR(65535) |
| JSON 字段用 JSON 类型 | `config JSON` | MySQL 8 原生支持，可以做函数索引 |
| 外键关联存 ID，不建物理外键 | `model_provider_id BIGINT` | 不用 FOREIGN KEY 约束，应用层保证一致性 |
| 字符集统一 utf8mb4 | `CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci` | 支持中文和 emoji |

### 主键策略

```yaml
# application.yml
mybatis-plus:
  global-config:
    db-config:
      id-type: assign_id    # 雪花算法，19位数字，趋势递增
```

不使用数据库自增 ID，原因：
- 分布式安全（未来拆分不需要改）
- 批量插入不需要先查 last_insert_id
- 不会暴露用户量（自增 ID 可推算）

### 软删除

```yaml
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: isDeleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

```java
// Entity 中
@TableLogic
private Integer isDeleted;
```

所有业务表用软删除。MyBatis-Plus 自动在查询中追加 `WHERE is_deleted = 0`。

---

## 二、索引设计原则

### 规则 1：通过慢查询日志发现需要索引，不提前建无用索引

```sql
-- MySQL 开启慢查询日志（一期部署后开启）
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 0.5;    -- 超过 500ms 记录
SET GLOBAL slow_query_log_file = '/var/log/mysql/slow.log';
```

一期启动后跑一周，根据 slow.log 决定加什么索引。

### 规则 2：索引命名

| 类型 | 命名 | 示例 |
|------|------|------|
| 普通索引 | `idx_表名_字段名` | `idx_agent_status` |
| 唯一索引 | `uk_表名_字段名` | `uk_user_username` |
| 联合索引 | `idx_表名_字段1_字段2` | `idx_message_conversation_created` |

### 规则 3：联合索引遵循最左前缀，把区分度高的列放前面

```sql
-- ✓ 区分度：conversation_id (高) > created_at (低)
CREATE INDEX idx_message_conversation_created
ON conversation_message (conversation_id, created_at);

-- 这个索引同时覆盖以下查询：
-- WHERE conversation_id = ?                          ✓ 走索引
-- WHERE conversation_id = ? ORDER BY created_at      ✓ 走索引，排序也走索引
-- WHERE conversation_id = ? AND created_at > ?       ✓ 走索引
-- WHERE created_at = ?                               ✗ 不走索引（缺少最左列）
```

判断区分度的方式：

```sql
-- 查看字段的基数（区分度越高越适合放前面）
SELECT COUNT(DISTINCT conversation_id) / COUNT(*) AS conversation_id_sel,
       COUNT(DISTINCT created_at) / COUNT(*) AS created_at_sel
FROM conversation_message;
-- conversation_id_sel 越接近 1，区分度越高
```

### 规则 4：单表索引数量不超过 5 个

每个索引增加写入开销（INSERT/UPDATE/DELETE 要同步更新索引）。超过 5 个说明表职责可能不清晰，考虑拆分。

### 规则 5：不在低区分度字段上建单列索引

```sql
-- ✗ 不要这样做
CREATE INDEX idx_agent_status ON agent (status);
-- status 只有 3-5 个值（草稿/启用/停用），MySQL 优化器大概率选择全表扫描

-- ✓ 如果确实需要按 status 过滤，和其他字段组合
CREATE INDEX idx_agent_user_status ON agent (user_id, status);
```

### 规则 6：TEXT / JSON 字段不建普通索引

```sql
-- ✗ 不支持
CREATE INDEX idx_agent_config ON agent (config);   -- JSON 类型不能直接建 B-Tree 索引

-- ✓ 如果需要按 JSON 内部字段查询，用虚拟生成列 + 索引
ALTER TABLE agent ADD COLUMN model_id VARCHAR(64)
    GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(config, '$.modelId'))) STORED;
CREATE INDEX idx_agent_model_id ON agent (model_id);
```

### 规则 7：所有索引必须有明确的业务查询对应

建索引前回答：哪条 SQL 会用到这个索引？如果答不出来，不建。

---

## 三、大表预判和应对策略

### 预判：哪些表会变大

| 表 | 预估增长速度 | 一年预估行数 | 风险等级 |
|------|-------------|-------------|---------|
| `conversation_message` | 50 人 × 20 条/天/人 = 1000 条/天 | 36 万条 | 中 |
| `workflow_run_log` | 50 人 × 5 次/天/人 = 250 条/天 | 9 万条 | 低 |
| `document_chunk` | 取决于文档上传量 | 几万~几十万条 | 中 |
| `agent` | 50 人 × 3 个/人 | 几百条 | 无 |
| `user` | 50 条 | 50 条 | 无 |
| `knowledge` | 几十个 | 几十条 | 无 |
| `model_provider` | 几条 | 几条 | 无 |

### 大表应对策略：conversation_message

这是唯一需要提前规划的大表。

**策略：冷热分离（不拆表，用索引 + 定期归档）**

```sql
CREATE TABLE conversation_message (
    id              BIGINT       NOT NULL,
    conversation_id BIGINT       NOT NULL,
    role            VARCHAR(16)  NOT NULL COMMENT 'user/assistant/system',
    content         TEXT         NOT NULL,
    token_count     INT          NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 核心查询：加载某个对话的最近 N 条消息
    INDEX idx_message_conversation_created (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**一期不做归档，但预留归档能力：**

```sql
-- 二期归档方案（超过 90 天的对话消息移到历史表）
-- 归档表结构和 conversation_message 完全一致
CREATE TABLE conversation_message_archive (
    -- 同 conversation_message
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 归档存储过程（二期启用）
-- INSERT INTO conversation_message_archive SELECT * FROM conversation_message
--   WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY) AND is_deleted = 0;
-- DELETE FROM conversation_message WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);
```

### 大表应对策略：document_chunk（pgvector）

```sql
CREATE TABLE document_chunk (
    id              BIGSERIAL    PRIMARY KEY,
    knowledge_id    BIGINT       NOT NULL,
    document_id     BIGINT       NOT NULL,
    content         TEXT         NOT NULL,
    embedding       vector(1536),
    token_count     INT          NOT NULL DEFAULT 0,
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- 业务查询：按知识库查文档块
    INDEX idx_chunk_knowledge_document (knowledge_id, document_id)
);

-- 向量检索索引
-- 一期：几万条用 IVFFlat
CREATE INDEX idx_chunk_embedding ON document_chunk
USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 二期迁移：超过 50 万条换 HNSW
-- DROP INDEX idx_chunk_embedding;
-- CREATE INDEX idx_chunk_embedding ON document_chunk
-- USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
```

**pgvector 索引选择规则：**

| 向量数量 | 索引类型 | lists/m 值 | 查询延迟 |
|----------|---------|-----------|---------|
| < 1 万 | 不建索引（暴力搜索就够） | — | < 10ms |
| 1 万 - 50 万 | IVFFlat | lists = sqrt(行数) | 10-100ms |
| > 50 万 | HNSW | m=16, ef_construction=64 | 10-50ms |

---

## 四、分页查询注意事项

### 规则 1：禁止不带 ORDER BY 的分页

```sql
-- ✗ 不加 ORDER BY，不同页之间可能出现重复行或遗漏行
SELECT * FROM agent WHERE is_deleted = 0 LIMIT 10 OFFSET 20;

-- ✓ 必须指定排序
SELECT * FROM agent WHERE is_deleted = 0 ORDER BY id LIMIT 10 OFFSET 20;
```

### 规则 2：深度分页用游标法，不用 OFFSET

```sql
-- ✗ OFFSET 10000 时 MySQL 要扫描 10010 行然后丢掉前 10000 行
SELECT * FROM conversation_message
WHERE conversation_id = 123
ORDER BY id LIMIT 10 OFFSET 10000;

-- ✓ 游标法：WHERE id > 上一页最后一条的 id，只扫描 10 行
SELECT * FROM conversation_message
WHERE conversation_id = 123 AND id > #{lastId}
ORDER BY id ASC LIMIT 10;
```

MyBatis-Plus 配合：

```java
// 对话消息的分页查询
public List<ConversationMessage> listMessages(Long conversationId, Long lastId, int pageSize) {
    return lambdaQuery()
        .eq(ConversationMessage::getConversationId, conversationId)
        .gt(lastId != null, ConversationMessage::getId, lastId)
        .orderByAsc(ConversationMessage::getId)
        .last("LIMIT " + pageSize)
        .list();
}
```

### 规则 3：管理后台的通用列表页可以用 OFFSET（页数浅）

```java
// 应用列表、Agent 列表等管理页面，用户一般只看前几页
// MyBatis-Plus 的 Page 对象即可
public Page<Agent> listAgents(AgentQueryRequest request) {
    Page<Agent> page = new Page<>(request.getPage(), request.getPageSize());
    return agentMapper.selectPage(page,
        new LambdaQueryWrapper<Agent>()
            .like(StringUtils.hasText(request.getName()), Agent::getName, request.getName())
            .eq(request.getStatus() != null, Agent::getStatus, request.getStatus())
            .orderByDesc(Agent::getCreatedAt)
    );
}
```

**OFFSET 的安全上限：**

```java
// 在 Request 中限制 pageSize 和 page
@Data
public class PageRequest {
    @Min(1) @Max(100)
    private int pageSize = 20;

    @Min(1) @Max(100)    // 最多翻 100 页，防止深度分页
    private int page = 1;
}
```

### 规则 4：COUNT 查询不要扫全表

```sql
-- ✗ 全表扫描
SELECT COUNT(*) FROM conversation_message WHERE is_deleted = 0;

-- ✓ 加条件缩小范围
SELECT COUNT(*) FROM conversation_message
WHERE conversation_id = #{conversationId} AND is_deleted = 0;

-- ✓ 列表总数估算（不要求精确时）
-- MyBatis-Plus 的 Page 对象会自动执行 COUNT，确保 WHERE 条件有索引即可
```

---

## 五、建表模板

### MySQL 业务表模板

```sql
-- Flyway 迁移文件: V1__init_schema.sql

-- 用户表
CREATE TABLE `user` (
    `id`          BIGINT       NOT NULL,
    `username`    VARCHAR(64)  NOT NULL,
    `password`    VARCHAR(128) NOT NULL,
    `display_name` VARCHAR(64),
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Agent 表
CREATE TABLE `agent` (
    `id`                BIGINT       NOT NULL,
    `name`              VARCHAR(100) NOT NULL,
    `description`       VARCHAR(500),
    `system_prompt`     TEXT,
    `config`            JSON         COMMENT 'Agent 配置（模型、温度、工具等）',
    `user_id`           BIGINT       NOT NULL,
    `model_provider_id` BIGINT       NOT NULL,
    `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '1-草稿 2-启用 3-停用',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`        TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_agent_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 对话表
CREATE TABLE `conversation` (
    `id`          BIGINT       NOT NULL,
    `title`       VARCHAR(200),
    `agent_id`    BIGINT       NOT NULL,
    `user_id`     BIGINT       NOT NULL,
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-进行中 2-已结束',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_conversation_user_id` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 对话消息表
CREATE TABLE `conversation_message` (
    `id`              BIGINT       NOT NULL,
    `conversation_id` BIGINT       NOT NULL,
    `role`            VARCHAR(16)  NOT NULL COMMENT 'user/assistant/system/tool',
    `content`         TEXT         NOT NULL,
    `token_count`     INT          NOT NULL DEFAULT 0,
    `metadata`        JSON         COMMENT '工具调用结果、引用来源等',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`      TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_message_conversation_created` (`conversation_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 工作流表
CREATE TABLE `workflow` (
    `id`          BIGINT       NOT NULL,
    `name`        VARCHAR(100) NOT NULL,
    `description` VARCHAR(500),
    `graph`       JSON         NOT NULL COMMENT '工作流 DAG 定义',
    `user_id`     BIGINT       NOT NULL,
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-草稿 2-启用 3-停用',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_workflow_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 工作流运行日志
CREATE TABLE `workflow_run_log` (
    `id`          BIGINT       NOT NULL,
    `workflow_id` BIGINT       NOT NULL,
    `user_id`     BIGINT       NOT NULL,
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-运行中 2-成功 3-失败',
    `inputs`      JSON,
    `outputs`     JSON,
    `error`       TEXT,
    `elapsed_ms`  INT,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_run_log_workflow_created` (`workflow_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 知识库表
CREATE TABLE `knowledge` (
    `id`          BIGINT       NOT NULL,
    `name`        VARCHAR(100) NOT NULL,
    `description` VARCHAR(500),
    `user_id`     BIGINT       NOT NULL,
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-构建中 2-就绪 3-失败',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_knowledge_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 模型提供商
CREATE TABLE `model_provider` (
    `id`          BIGINT       NOT NULL,
    `name`        VARCHAR(64)  NOT NULL,
    `provider_type` VARCHAR(32) NOT NULL COMMENT 'openai/anthropic/google/ollama',
    `api_key`     VARCHAR(256),
    `base_url`    VARCHAR(256) NOT NULL,
    `config`      JSON         COMMENT '超时、重试等配置',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- MCP 工具
CREATE TABLE `mcp_tool` (
    `id`          BIGINT       NOT NULL,
    `name`        VARCHAR(64)  NOT NULL,
    `description` VARCHAR(500),
    `server_url`  VARCHAR(256) NOT NULL,
    `openapi_schema` JSON      COMMENT '工具参数定义',
    `config`      JSON,
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### pgvector 向量表模板

```sql
-- PostgreSQL: 单独在 hify_vector 库中执行
-- Flyway 不管理，用初始化脚本

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunk (
    id              BIGSERIAL    PRIMARY KEY,
    knowledge_id    BIGINT       NOT NULL,
    document_id     BIGINT       NOT NULL,
    content         TEXT         NOT NULL,
    embedding       vector(1536),
    token_count     INT          NOT NULL DEFAULT 0,
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    INDEX idx_chunk_knowledge_document (knowledge_id, document_id)
);

-- 一期：IVFFlat 索引（注意：表中需先有数据再建 IVFFlat 索引）
-- 首次部署可以先不建，等数据超过 1 万条后执行：
-- CREATE INDEX idx_chunk_embedding ON document_chunk
-- USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

---

## 六、数据库迁移管理

用 Flyway 管理表结构变更：

```
src/main/resources/
  db/migration/
    V1__init_schema.sql            -- 首次建表（上面的模板）
    V2__add_agent_fallback_chain.sql  -- 后续变更
```

**规则：**
- 版本号只增不减：V1 → V2 → V3，不改已执行的文件
- 每个迁移文件只做一件事，文件名说清楚做了什么
- 不在迁移文件里写数据清洗逻辑（数据修复用单独脚本）
- 测试环境先跑一遍再应用到生产
