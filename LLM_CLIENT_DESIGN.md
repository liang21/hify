# Hify LLM 调用层技术方案

## 整体架构

```
请求线程 (Tomcat, 200 threads)
    │
    │  @Async 或 CompletableFuture
    ▼
LLM 线程池 (专用的 ThreadPoolTaskExecutor)
    │
    │  Resilience4j Bulkhead (限制并发)
    ▼
Resilience4j CircuitBreaker (熔断判断)
    │
    │  CLOSED → 正常调用
    │  OPEN   → 直接失败，走 Fallback
    ▼
Resilience4j Retry (重试)
    │
    ▼
OkHttp
    │
    │  Connect Timeout → Read Timeout
    ▼
外部 LLM API (OpenAI / Claude / Gemini / Ollama)
```

---

## 1. 线程管理

### 问题

Tomcat 默认 200 线程。LLM 调用一次 5-60 秒。如果不隔离，50 个并发对话就能把 Tomcat 线程池耗尽，所有接口（包括静态页面）都会卡死。

### 方案：专用 LLM 线程池

```java
@Configuration
public class LlmExecutorConfig {

    @Bean("llmTaskExecutor")
    public ThreadPoolTaskExecutor llmTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);       // 常驻线程，匹配 LLM API 的并发上限
        executor.setMaxPoolSize(30);        // 突发上限
        executor.setQueueCapacity(100);     // 排队等待的任务数
        executor.setThreadNamePrefix("llm-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 队列满了由调用线程执行，不丢弃任务，但会阻塞 Tomcat 线程作为背压信号
        executor.initialize();
        return executor;
    }
}
```

### 调用方式

**非流式调用：** 用 `@Async` + `CompletableFuture`

```java
@Service
@RequiredArgsConstructor
public class LlmService {

    @Qualifier("llmTaskExecutor")
    private final ThreadPoolTaskExecutor llmExecutor;
    private final LlmClientFactory clientFactory;

    // 异步调用，返回 CompletableFuture，Tomcat 线程立即释放
    public CompletableFuture<LlmResponse> chat(LlmRequest request) {
        return CompletableFuture.supplyAsync(
            () -> doChat(request),
            llmExecutor
        );
    }

    // 同步调用（工作流节点内部使用，已在 LLM 线程池中）
    public LlmResponse chatSync(LlmRequest request) {
        return doChat(request);
    }

    private LlmResponse doChat(LlmRequest request) {
        LlmClient client = clientFactory.getClient(request.getProviderId());
        return client.chat(request);
    }
}
```

**流式调用（SSE）：** 用 SseEmitter + OkHttp 流式读取，纯 Spring MVC，不需要 WebFlux

```java
@GetMapping(value = "/api/v1/conversations/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamChat(@PathVariable Long id, @RequestBody ChatRequest request) {
    // 超时 5 分钟，LLM 长响应不至于断开
    SseEmitter emitter = new SseEmitter(300_000L);

    llmTaskExecutor.execute(() -> {
        try {
            llmService.streamChat(request, new LlmStreamCallback() {
                @Override
                public void onChunk(String chunk) {
                    emitter.send(SseEmitter.event().data(chunk));
                }

                @Override
                public void onComplete() {
                    emitter.complete();
                }

                @Override
                public void onError(Exception e) {
                    emitter.completeWithError(e);
                }
            });
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });

    // 注册客户端断开回调，及时清理资源
    emitter.onCompletion(() -> log.debug("SSE connection closed: conversation={}", id));
    emitter.onTimeout(() -> log.warn("SSE connection timeout: conversation={}", id));

    return emitter;
}
```

```java
// LlmClient 实现类中：用 OkHttp 流式读取，逐块回调
public void streamChat(LlmRequest request, LlmStreamCallback callback) {
    Call call = streamingHttpClient.newCall(buildStreamRequest(request));

    try (Response response = call.execute()) {
        if (!response.isSuccessful()) {
            throw new LlmHttpException(response.code(), "Stream request failed");
        }
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6);
                if ("[DONE]".equals(data)) {
                    break;
                }
                callback.onChunk(parseChunk(data));
            }
        }
        callback.onComplete();
    } catch (Exception e) {
        callback.onError(e);
    }
}
```

```java
// 回调接口
public interface LlmStreamCallback {
    void onChunk(String chunk);
    void onComplete();
    void onError(Exception e);
}
```

### 线程隔离总结

| 场景 | 线程 | 说明 |
|------|------|------|
| 普通页面 / CRUD API | Tomcat 线程 | 毫秒级响应，不走 LLM 线程池 |
| 非流式 LLM 调用 | llm- 线程池 | CompletableFuture，Tomcat 线程立即释放 |
| 流式 LLM 调用 (SSE) | llm- 线程池 + SseEmitter | Tomcat 线程 return 后释放，SSE 由异步 Servlet 保持，读取在 llm 线程池中 |
| 工作流节点执行 | llm- 线程池 | 工作流引擎整体在 llm 线程池中运行 |

---

## 2. 超时

### 三级超时

```
连接超时 (Connect Timeout)
│  建立 TCP 连接的最大等待时间
│  本地 Ollama: 5s，远程 API: 10s
│
读取超时 (Read Timeout)
│  连接建立后，等待响应数据的最长时间
│  非流式: 120s，流式: 单个 chunk 间隔 60s
│
总超时 (Overall Timeout)
   从请求发出到完整响应的最大时间
   包含重试在内，上限 300s
```

### OkHttp 客户端配置

```java
@Configuration
public class HttpClientConfig {

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                // 连接池：保持长连接，减少 TCP 握手
                .connectionPool(new ConnectionPool(
                    20,         // 最大空闲连接数
                    5, TimeUnit.MINUTES  // 空闲存活时间
                ))
                // 失败后换 DNS 或重连
                .retryOnConnectionFailure(true)
                .build();
    }

    // 流式专用：readTimeout 设为 0（无限制），靠 chunk 间隔超时控制
    @Bean
    public OkHttpClient streamingHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)     // 流式不设整体 readTimeout
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
                .build();
    }
}
```

### 每个 Provider 可覆盖默认超时

```java
@Data
@ConfigurationProperties(prefix = "hify.llm.provider")
public class LlmProviderProperties {
    private Map<String, ProviderConfig> providers = new HashMap<>();

    @Data
    public static class ProviderConfig {
        private int connectTimeout = 10;    // 秒
        private int readTimeout = 120;      // 秒
        private int maxRetries = 3;
        private int retryInterval = 1;      // 秒，初始退避时间
    }
}
```

```yaml
# application.yml
hify:
  llm:
    provider:
      providers:
        openai:
          connect-timeout: 10
          read-timeout: 120
          max-retries: 3
        ollama:
          connect-timeout: 5       # 本地网络快
          read-timeout: 300        # 本地大模型可能很慢
          max-retries: 1           # 本地不需要重试太多
        claude:
          connect-timeout: 10
          read-timeout: 120
          max-retries: 3
```

---

## 3. 重试

### 规则

| HTTP 状态码 | 是否重试 | 理由 |
|-------------|----------|------|
| 429 (Rate Limit) | 是 | 临时限流，等一会就好 |
| 500, 502, 503, 504 | 是 | 服务端临时故障 |
| SocketTimeoutException | 是 | 网络超时 |
| ConnectException | 是 | 连接失败 |
| 400, 401, 403, 404 | 否 | 客户端错误，重试无意义 |
| 其他 | 否 | 保守策略，不重试未知错误 |

### 退避策略：指数退避 + 抖动

```
重试间隔 = baseInterval × 2^attempt + random(0, baseInterval)
第1次重试: 1s + jitter
第2次重试: 2s + jitter
第3次重试: 4s + jitter
```

不加抖动的后果：高并发时所有请求同时重试，再次触发限流（惊群效应）。

### 429 特殊处理：尊重 Retry-After 头

```java
// 如果响应头有 Retry-After，用它的值代替计算出的退避时间
// Retry-After: 5  表示 5 秒后重试
```

### 代码实现

不用 Resilience4j 的 Retry（配置太复杂），手写一个透明的重试包装器更可控：

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmRetryExecutor {

    private final LlmProviderProperties properties;

    private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 500, 502, 503, 504);
    private static final Random JITTER = new Random();

    public <T> T executeWithRetry(String providerId, Supplier<T> action) {
        ProviderConfig config = properties.getProviders().getOrDefault(
            providerId, new ProviderConfig()
        );
        int maxRetries = config.getMaxRetries();
        int baseInterval = config.getRetryInterval();

        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = calculateDelay(baseInterval, attempt);
                    log.warn("LLM call retry attempt {}/{}, waiting {}ms for provider {}",
                            attempt, maxRetries, delay, providerId);
                    Thread.sleep(delay);
                }
                return action.get();

            } catch (LlmHttpException e) {
                lastException = e;
                if (!shouldRetry(e, attempt, maxRetries)) {
                    throw e;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("LLM call interrupted");
            }
        }

        throw new BusinessException("LLM call failed after " + maxRetries + " retries: "
                + lastException.getMessage());
    }

    private long calculateDelay(int baseInterval, int attempt) {
        long exponential = baseInterval * 1000L * (1L << (attempt - 1));
        long jitter = JITTER.nextLong(baseInterval * 1000L);
        return exponential + jitter;
    }

    private boolean shouldRetry(LlmHttpException e, int attempt, int maxRetries) {
        if (attempt >= maxRetries) {
            return false;
        }
        return RETRYABLE_STATUS.contains(e.getStatusCode())
                || e.getCause() instanceof SocketTimeoutException
                || e.getCause() instanceof ConnectException;
    }
}
```

### 在 LlmClient 中使用

```java
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleClient implements LlmClient {

    private final OkHttpClient httpClient;
    private final LlmRetryExecutor retryExecutor;

    @Override
    public LlmResponse chat(LlmRequest request) {
        return retryExecutor.executeWithRetry(request.getProviderId(), () -> {
            // 实际的 HTTP 调用
            // 失败时抛出 LlmHttpException(statusCode, cause)
        });
    }
}
```

---

## 4. 容错

### 熔断器（Circuit Breaker）

**目的：** 当某个 Provider 持续失败时，不再浪费时间调用它，直接走 Fallback 或快速失败。

```
状态机：
CLOSED ──(失败率超阈值)──→ OPEN ──(等待冷却)──→ HALF_OPEN
  ↑                                                    │
  └────────────(探测成功)────────────────────────────────┘
  OPEN ──(探测失败)──→ OPEN (重置冷却计时)
```

```yaml
# application.yml - Resilience4j 配置
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 10          # 最近 10 次调用
        failure-rate-threshold: 50       # 失败率 > 50% 则熔断
        wait-duration-in-open-state: 30s # 熔断后等 30 秒再探测
        permitted-number-of-calls-in-half-open-state: 3  # 半开时放 3 个请求探测
        minimum-number-of-calls: 5       # 至少 5 次调用才开始计算失败率
        automatic-transition-from-open-to-half-open-enabled: true
    instances:
      openai:
        base-config: default
      claude:
        base-config: default
      gemini:
        base-config: default
      ollama:
        base-config: default
        failure-rate-threshold: 80       # 本地 Ollama 容忍度高一些
```

### Fallback：Provider 降级链

```yaml
# application.yml
hify:
  llm:
    fallback:
      chains:
        default: openai, claude, ollama       # 默认降级链
        coding:  claude, openai, ollama       # 编码场景 Claude 优先
```

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmClientFactory {

    private final List<LlmClient> clients;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final LlmProviderProperties properties;

    /**
     * 带降级的调用：依次尝试降级链中的 Provider
     */
    public LlmResponse chatWithFallback(LlmRequest request) {
        List<String> chain = resolveChain(request);

        for (String providerId : chain) {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(providerId);
            if (cb.getState() == CircuitBreaker.State.OPEN) {
                log.warn("Provider {} is OPEN, skipping", providerId);
                continue;
            }
            try {
                LlmClient client = getClient(providerId);
                // 拷贝请求，替换 providerId
                LlmRequest actualRequest = request.withProviderId(providerId);
                return cb.executeSupplier(() -> client.chat(actualRequest));
            } catch (Exception e) {
                log.warn("Provider {} failed: {}", providerId, e.getMessage());
                // 继续尝试下一个 Provider
            }
        }

        throw new BusinessException("All providers in fallback chain failed");
    }

    /**
     * 解析降级链
     */
    private List<String> resolveChain(LlmRequest request) {
        // 如果请求指定了降级链，用它；否则用配置的 default
        if (request.getFallbackChain() != null) {
            return request.getFallbackChain();
        }
        String chainName = request.getFallbackChainName() != null
                ? request.getFallbackChainName() : "default";
        return properties.getFallback().getChains().get(chainName);
    }
}
```

### 隔离仓（Bulkhead）—— 限制并发调用数

**目的：** 防止某个 Provider 的慢响应占满所有 LLM 线程，拖垮其他 Provider 的调用。

```yaml
resilience4j:
  bulkhead:
    configs:
      default:
        max-concurrent-calls: 10      # 每个 Provider 最多 10 个并发
        max-wait-duration: 30s        # 排队最多等 30 秒
    instances:
      openai:
        base-config: default
        max-concurrent-calls: 15      # OpenAI 并发上限高
      claude:
        base-config: default
      gemini:
        base-config: default
      ollama:
        base-config: default
        max-concurrent-calls: 5       # 本地 Ollama 并发低
```

```java
// 在 LlmClientFactory 中组合 Bulkhead + CircuitBreaker
public LlmResponse chatWithFallback(LlmRequest request) {
    List<String> chain = resolveChain(request);

    for (String providerId : chain) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(providerId);
        Bulkhead bh = bulkheadRegistry.bulkhead(providerId);

        if (cb.getState() == CircuitBreaker.State.OPEN) {
            continue;
        }
        try {
            LlmClient client = getClient(providerId);
            return cb.executeSupplier(() ->
                bh.executeSupplier(() -> client.chat(request.withProviderId(providerId)))
            );
        } catch (BulkheadFullException e) {
            log.warn("Provider {} bulkhead full, trying next", providerId);
        } catch (Exception e) {
            log.warn("Provider {} failed: {}", providerId, e.getMessage());
        }
    }

    throw new BusinessException("All providers failed or unavailable");
}
```

---

## 完整调用链（代码串联）

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {

    @Qualifier("llmTaskExecutor")
    private final ThreadPoolTaskExecutor llmExecutor;
    private final LlmClientFactory clientFactory;

    /**
     * 异步对话（Controller 层调用）
     * 线程：Tomcat → llmTaskExecutor → OkHttp
     * 容错：Bulkhead → CircuitBreaker → Retry → Fallback
     */
    public CompletableFuture<LlmResponse> chatAsync(LlmRequest request) {
        return CompletableFuture.supplyAsync(
            () -> clientFactory.chatWithFallback(request),
            llmExecutor
        ).orTimeout(300, TimeUnit.SECONDS);  // 总超时兜底，含重试
    }

    /**
     * 同步对话（Workflow 引擎内部调用，已在 llmExecutor 中）
     */
    public LlmResponse chatSync(LlmRequest request) {
        return clientFactory.chatWithFallback(request);
    }

    /**
     * 流式对话（SSE + SseEmitter）
     * 在 llmTaskExecutor 中执行 OkHttp 流式读取，通过 callback 逐块推送
     */
    public void streamChat(LlmRequest request, LlmStreamCallback callback) {
        LlmClient client = clientFactory.getClient(request.getProviderId());
        client.streamChat(request, callback);
    }
}
```

---

## 监控埋点

```java
@Component
@Slf4j
public class LlmCallLogger {

    // 每次调用记录一条日志，用于后续分析
    public void log(String providerId, String model, long latencyMs,
                    boolean success, String errorType) {
        log.info("LLM_CALL provider={} model={} latency={}ms success={} error={}",
                providerId, model, latencyMs, success, errorType);
    }
}
```

在 `LlmClient` 实现类中调用：

```java
@Override
public LlmResponse chat(LlmRequest request) {
    long start = System.currentTimeMillis();
    try {
        LlmResponse response = doHttpCall(request);
        callLogger.log(request.getProviderId(), request.getModel(),
                System.currentTimeMillis() - start, true, null);
        return response;
    } catch (Exception e) {
        callLogger.log(request.getProviderId(), request.getModel(),
                System.currentTimeMillis() - start, false, e.getClass().getSimpleName());
        throw e;
    }
}
```

---

## Maven 依赖

```xml
<!-- OkHttp: HTTP 客户端 -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>

<!-- Resilience4j: 熔断 + 隔离仓 -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-circuitbreaker</artifactId>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-bulkhead</artifactId>
</dependency>

<!-- 不需要 spring-boot-starter-webflux -->
<!-- SSE 由 Spring MVC 的 SseEmitter 原生支持 -->
```

---

## 方案速查表

| 维度 | 方案 | 关键参数 |
|------|------|----------|
| 线程管理 | 专用 ThreadPoolTaskExecutor | core=10, max=30, queue=100 |
| 连接超时 | OkHttp connectTimeout | 10s (远程), 5s (本地) |
| 读取超时 | OkHttp readTimeout | 120s (非流式), 0 (流式) |
| 总超时 | CompletableFuture.orTimeout | 300s (含重试) |
| 重试 | 手写 RetryExecutor | max=3, 指数退避+jitter |
| 熔断 | Resilience4j CircuitBreaker | 失败率>50%, 冷却30s |
| 隔离 | Resilience4j Bulkhead | 每 Provider 10 并发 |
| 降级 | Provider 降级链 | openai→claude→ollama |
| 流式 | SseEmitter + OkHttp 流式读取 | 纯 Spring MVC，不需要 WebFlux |
