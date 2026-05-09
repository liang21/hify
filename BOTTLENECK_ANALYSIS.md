# Hify 性能瓶颈分析

## 排序规则

严重程度 = 发生概率 × 影响范围 × 恢复难度

---

## 瓶颈 1：LLM API 响应延迟（最严重）

**触发条件：** 任何时候，每个对话都触发。单次调用 5-60 秒。

**影响范围：** 全部对话功能不可用 = 系统不可用。

**具体机制：**

```
llmTaskExecutor: core=10, max=30, queue=100

场景：30 人同时发消息
├─ 30 个 llm 线程被占用（每个等 LLM 响应 5-30s）
├─ 第 31-130 人排队（queue=100）
├─ 第 131 人 → CallerRunsPolicy → Tomcat 线程被阻塞
├─ 第 132 人 → 另一个 Tomcat 线程被阻塞
├─ ...累积阻塞
└─ Tomcat 线程耗尽 → 所有接口（包括静态页面）无响应
   → 健康检查失败 → K8s 重启 Pod → 在线用户断开
```

**一期是否处理：必须。** 这是系统的命脉。

**一期处理措施（已设计中，无需新增）：**

| 措施 | 状态 | 作用 |
|------|------|------|
| 专用 llmTaskExecutor | 已设计 | 隔离 LLM 调用，不直接占 Tomcat 线程 |
| Resilience4j Bulkhead | 已设计 | 每 Provider 限并发，防止单个 Provider 拖垮全局 |
| CircuitBreaker 熔断 | 已设计 | Provider 挂了快速失败，不排队空等 |
| Provider 降级链 | 已设计 | OpenAI 慢了切 Claude，保持可用 |
| CompletableFuture.orTimeout(300s) | 已设计 | 兜底超时，不无限等待 |

**需要额外加的一条防御：** 对队列积压加监控告警。

```java
// 在 LlmExecutorConfig 中加一个定时检查
@Scheduled(fixedRate = 5000)
public void monitorExecutor() {
    int queueSize = llmExecutor.getThreadPoolExecutor().getQueue().size();
    int activeThreads = llmExecutor.getActiveCount();
    if (queueSize > 50 || activeThreads >= llmExecutor.getMaxPoolSize()) {
        log.warn("LLM executor under pressure: active={}, queue={}, max={}",
                activeThreads, queueSize, llmExecutor.getMaxPoolSize());
    }
}
```

---

## 瓶颈 2：LLM API 速率限制（严重）

**触发条件：** 并发用户超过 Provider 的 RPM/TPM 限额。OpenAI Tier 1 默认 500 RPM，Claude 默认按 Tier 限制。

**影响范围：** 触发 429 后所有该 Provider 的请求排队等重试，延迟雪崩。

**具体机制：**

```
OpenAI Tier 1: 500 RPM = 约 8 RPS
如果 20 人同时对话，每人 1 次请求 → 瞬时 20 RPS
→ 大量 429
→ 重试（1s + 2s + 4s）
→ 重试请求又叠加
→ 延迟从 5s 涨到 30s+
```

**一期是否处理：必须。** 50 人场景很容易触发。

**一期处理措施：**

加一个全局限流器，在应用层控制对外部 API 的请求速率，避免撞上 Provider 限制后被大量 429 反复重试。

```java
@Component
public class ProviderRateLimiter {

    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    // 每个 Provider 一个令牌桶
    public boolean tryAcquire(String providerId) {
        RateLimiter limiter = limiters.computeIfAbsent(
            providerId,
            id -> RateLimiter.create(getRateLimit(id))  // Guava RateLimiter
        );
        return limiter.tryAcquire(5, TimeUnit.SECONDS);
    }
}
```

```java
// 在 LlmClientFactory.chatWithFallback 中调用
for (String providerId : chain) {
    if (!rateLimiter.tryAcquire(providerId)) {
        log.warn("Provider {} rate limit reached locally, trying next", providerId);
        continue;  // 不发请求，直接尝试下一个 Provider
    }
    // ... 正常调用
}
```

---

## 瓶颈 3：对话上下文膨胀导致 Prompt 过长（中等）

**触发条件：** 用户连续对话超过 20-30 轮，上下文 Token 数超过模型限制（如 128K）。

**影响范围：** 该对话失败或被截断，不影响其他用户。

**具体机制：**

```
每轮对话约 200-500 tokens
30 轮后上下文约 6000-15000 tokens
加上 RAG 检索的文档片段（可能 5000-10000 tokens）
加上 System Prompt（可能 1000-2000 tokens）
总 Prompt: 12000-27000 tokens
→ 还好

但极端情况：
100 轮对话 + 5 篇长文档 + System Prompt
→ 200K+ tokens → 超限 → API 报错
```

**一期是否处理：需要。** 不是性能瓶颈但会导致功能失败。

**一期处理措施：** 在调用 LLM 前检查 Token 数，超限时截断早期对话。

```java
@Component
public class ContextTrimmer {

    private static final int MAX_CONTEXT_TOKENS = 100_000;  // 留 20% 余量给模型输出

    public List<Message> trim(List<Message> messages, String systemPrompt, String ragContext) {
        int systemTokens = estimateTokens(systemPrompt + ragContext);
        int remaining = MAX_CONTEXT_TOKENS - systemTokens;

        // 从最新的消息开始保留，倒序累加
        List<Message> trimmed = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            int msgTokens = estimateTokens(messages.get(i).getContent());
            if (remaining - msgTokens < 0) break;
            remaining -= msgTokens;
            trimmed.add(0, messages.get(i));
        }
        return trimmed;
    }

    // 简单估算：中文约 1 字 = 1.5 token，英文约 4 字符 = 1 token
    private int estimateTokens(String text) {
        return (int) (text.length() * 1.2);
    }
}
```

---

## 瓶颈 4：Embedding 批量调用慢（中等）

**触发条件：** 用户上传大文档（>5MB）到知识库，需要分块后逐块调 Embedding API。

**影响范围：** 该文档处理慢，但不影响其他用户（异步处理）。

**具体机制：**

```
一个 10MB PDF → 分成约 500 个 chunk
每个 chunk 调一次 Embedding API → 约 0.5s
串行处理: 500 × 0.5s = 250s ≈ 4 分钟

用户上传后等 4 分钟才能检索，体验差
```

**一期是否处理：建议处理。** 投入小，收益大。

**一期处理措施：** 批量 Embedding + 异步处理。

```java
// 1. 批量调用：OpenAI Embedding API 支持一次传多个 input
public List<float[]> batchEmbed(List<String> texts) {
    // 每批最多 100 条（OpenAI 限制）
    List<List<String>> batches = Lists.partition(texts, 100);
    List<float[]> result = new ArrayList<>();
    for (List<String> batch : batches) {
        result.addAll(callEmbeddingApi(batch));
    }
    return result;
}

// 2. 异步处理：上传接口立即返回，后台任务处理分块和 Embedding
@Async("llmTaskExecutor")
public void processDocumentAsync(Long documentId) {
    // 1. 解析文档 → 分块
    // 2. 批量 Embedding
    // 3. 写入 pgvector
    // 4. 更新文档状态为"已就绪"
}
```

---

## 瓶颈 5：工作流长时间占用线程（中等）

**触发条件：** 工作流包含多个串行 LLM 节点，单个工作流执行耗时 1-5 分钟。

**影响范围：** 占用 llmTaskExecutor 线程，减少其他用户的可用线程。

**具体机制：**

```
一个 5 节点工作流：
节点1 (LLM) → 10s
节点2 (条件) → 0s
节点3 (LLM) → 15s
节点4 (HTTP) → 3s
节点5 (LLM) → 10s
总耗时: ~38s

这 38 秒内占用 1 个 llm 线程
如果 10 人同时跑复杂工作流 → 10 个线程被锁住近 1 分钟
→ 可用线程从 30 降到 20
```

**一期是否处理：不需要专门处理。** 现有线程池配置（max=30）够用。

**二期考虑：** 如果工作流使用频繁，可以将工作流执行拆成异步步骤，每个节点执行完释放线程，下一步重新提交到线程池。但一期复杂度不值得。

---

## 瓶颈 6：pgvector 查询性能（低）

**触发条件：** 向量数超过 50 万条，或并发检索请求 > 20 QPS。

**影响范围：** RAG 检索变慢，对话响应延迟增加 1-3 秒。

**具体机制：**

```
IVFFlat 索引在 lists=100 时：
- 1 万条向量：查询 < 10ms
- 10 万条向量：查询 < 50ms
- 50 万条向量：查询 100-500ms
- 100 万条向量：需要调大 lists 或换 HNSW 索引
```

**一期是否处理：不需要。** 50 人内部使用，文档量大概率在 1-10 万条，pgvector 绰绰有余。

**二期触发条件：** 向量数 > 50 万或检索延迟 > 500ms。处理方式：重建索引增大 lists，或换成 HNSW 索引。

```sql
-- 二期升级方案：换成 HNSW（更适合大规模）
CREATE INDEX idx_document_chunk_embedding
ON document_chunk USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

---

## 瓶颈 7：MySQL 对话消息表膨胀（低）

**触发条件：** 系统运行数月后，conversation_message 表行数超过百万。

**影响范围：** 查询历史对话变慢，但不影响实时对话。

**一期是否处理：不需要。** 一期数据量不会达到瓶颈。

**二期处理措施：**

```sql
-- 查询优化：加复合索引
CREATE INDEX idx_message_conversation_created
ON conversation_message (conversation_id, created_at);

-- 数据归档：超过 90 天的对话消息归档到历史表
-- 或者直接分表：按月分 conversation_message_2026_05
```

---

## 瓶颈 8：Redis 内存（低）

**触发条件：** 缓存了大量长对话上下文，Redis 内存用量接近 256Mi limit。

**影响范围：** 触发 Key 淘汰，对话上下文需要重新从 MySQL 加载，稍慢但不会失败。

**一期是否处理：不需要。** 256Mi 可以缓存数千个对话上下文，50 人绰绰有余。

---

## 总结

| # | 瓶颈 | 严重度 | 一期处理 | 措施 |
|---|------|--------|----------|------|
| 1 | LLM API 响应延迟 | **致命** | 已设计 | 线程池隔离 + 熔断 + 降级 + 超时（已有）+ 队列监控（新增） |
| 2 | LLM API 速率限制 | **严重** | **必须** | 应用层令牌桶限流（新增） |
| 3 | 对话上下文超 Token | **中等** | **需要** | ContextTrimmer 截断早期对话（新增） |
| 4 | Embedding 批量调用 | **中等** | **建议** | 批量 API + 异步处理（新增） |
| 5 | 工作流占用线程 | **中等** | 不需要 | 现有线程池配置够用 |
| 6 | pgvector 查询 | **低** | 不需要 | 一期数据量不触发 |
| 7 | MySQL 消息膨胀 | **低** | 不需要 | 加索引即可，数月后的事 |
| 8 | Redis 内存 | **低** | 不需要 | 256Mi 够 50 人用 |

**一期需要新增的代码：**
1. 队列监控（5 分钟写完）— `LlmExecutorConfig` 加 `@Scheduled`
2. Provider 限流器（30 分钟）— `ProviderRateLimiter` 组件
3. 上下文截断（30 分钟）— `ContextTrimmer` 组件
4. 文档异步处理 + 批量 Embedding（1 小时）— 改 `KnowledgeService`
