# Hify 代码组织规范

## 项目结构

```
com.hify/
├── agent/
│   ├── controller/
│   │   └── AgentController.java
│   ├── service/
│   │   ├── AgentService.java          (接口)
│   │   └── impl/
│   │       └── AgentServiceImpl.java
│   ├── mapper/
│   │   └── AgentMapper.java
│   ├── entity/
│   │   └── Agent.java
│   ├── dto/
│   │   ├── request/
│   │   │   └── CreateAgentRequest.java
│   │   └── response/
│   │       └── AgentDetailResponse.java
│   └── convertor/
│       └── AgentConvertor.java
│
├── conversation/
│   ├── controller/
│   ├── service/
│   │   └── impl/
│   ├── mapper/
│   ├── entity/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── convertor/
│   └── engine/                        (对话引擎，本模块专属)
│       ├── ConversationEngine.java
│       └── MessageBuilder.java
│
├── workflow/
│   ├── controller/
│   ├── service/
│   │   └── impl/
│   ├── mapper/
│   ├── entity/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── convertor/
│   └── engine/
│       ├── WorkflowEngine.java
│       ├── NodeExecutor.java
│       └── node/                      (节点类型实现)
│           ├── LlmNodeExecutor.java
│           ├── ConditionNodeExecutor.java
│           ├── CodeNodeExecutor.java
│           └── HttpNodeExecutor.java
│
├── knowledge/
│   ├── controller/
│   ├── service/
│   │   └── impl/
│   ├── mapper/
│   ├── entity/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── convertor/
│   └── rag/                           (RAG 子模块)
│       ├── Chunker.java
│       ├── EmbeddingService.java
│       ├── VectorStore.java
│       └── Retriever.java
│
├── modelprovider/
│   ├── controller/
│   ├── service/
│   │   └── impl/
│   ├── mapper/
│   ├── entity/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── convertor/
│   └── client/                        (LLM 客户端)
│       ├── LlmClient.java            (接口)
│       ├── LlmClientFactory.java
│       └── OpenAiCompatibleClient.java
│
├── mcp/
│   ├── controller/
│   ├── service/
│   │   └── impl/
│   ├── mapper/
│   ├── entity/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── convertor/
│   └── client/
│       ├── McpClient.java
│       └── McpClientFactory.java
│
└── common/
    ├── config/
    │   ├── SecurityConfig.java
    │   ├── WebConfig.java
    │   └── MybatisPlusConfig.java
    ├── exception/
    │   ├── BusinessException.java
    │   └── GlobalExceptionHandler.java
    └── util/
        └── JsonUtils.java
```

---

## 各层职责与规则

### 1. Controller 层

**职责：** 接收 HTTP 请求，参数校验，调用 Service，返回 Response。

```java
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping
    public R<Long> create(@RequestBody @Valid CreateAgentRequest request) {
        return R.ok(agentService.create(request));
    }
}
```

**规则：**
- 只做三件事：接收参数 → 调 Service → 包装返回值
- 参数校验用 `@Valid` + Bean Validation 注解（`@NotBlank`、`@NotNull`、`@Size`），不写 if-else 校验
- 禁止在 Controller 里写业务逻辑。判断标准：如果删掉 Controller 换成 gRPC/消息队列入口，逻辑不变，说明对了
- 返回值统一用 `R<T>` 包装，禁止直接返回 Entity 或 Map
- 路径命名：模块名复数 + RESTful，`/api/v1/agents`、`/api/v1/conversations`

### 2. Service 层

**职责：** 业务逻辑编排，事务管理。

```java
public interface AgentService {
    Long create(CreateAgentRequest request);
    AgentDetailResponse getDetail(Long id);
    PageResult<AgentListResponse> list(AgentQueryRequest request);
    void update(Long id, UpdateAgentRequest request);
    void delete(Long id);
}
```

**规则：**
- 接口和实现分离。接口在 `service/`，实现类在 `service/impl/`
- 实现类加 `@Service`，不加 `@Transactional`（事务在方法级别按需加）
- 需要事务的方法在实现类方法上加 `@Transactional(rollbackFor = Exception.class)`
- 只读方法不加 `@Transactional`
- 一个 Service 方法对应一个完整的业务用例，不要写"万能方法"
- Service 方法之间可以互相调用（同模块内），但要避免循环依赖。如果 AService 调 BService，BService 不能再调 AService

### 3. Mapper 层

**职责：** 数据库访问。

```java
@Mapper
public interface AgentMapper extends BaseMapper<Agent> {
    // 复杂查询写在这里，用 XML 或 @Select
}
```

**规则：**
- 继承 MyBatis-Plus 的 `BaseMapper<T>`，获得基础 CRUD
- 简单查询用 QueryWrapper/LambdaQueryWrapper，不写 SQL
- 复杂查询（多表关联、聚合）写 XML，XML 文件放 `resources/mapper/{模块名}/`
- 禁止在 Service 层拼 SQL 字符串
- Mapper 只被同模块的 Service 调用，禁止跨模块直接调 Mapper

### 4. Entity 层

**职责：** 数据库表映射。

```java
@TableName("agent")
@Data
public class Agent {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String description;
    private String systemPrompt;
    private String modelProviderId;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**规则：**
- 一个 Entity 对应一张表，字段名用 camelCase，MyBatis-Plus 自动转 snake_case
- 加 `@TableName` 指定表名，主键加 `@TableId`
- 禁止在 Entity 里加业务逻辑方法
- 禁止把 Entity 直接返回给 Controller 层或跨模块传递。用 Convertor 转成 DTO

### 5. DTO 层

**职责：** 接口间的数据传输对象。

```java
// request/ — 入参
@Data
public class CreateAgentRequest {
    @NotBlank(message = "name不能为空")
    @Size(max = 100)
    private String name;

    @NotBlank
    private String systemPrompt;

    @NotNull
    private Long modelProviderId;
}

// response/ — 出参
@Data
public class AgentDetailResponse {
    private Long id;
    private String name;
    private String description;
    private String systemPrompt;
    private String status;
    private LocalDateTime createdAt;
}
```

**规则：**
- Request 和 Response 是两个独立的类，不要混用
- Request 放校验注解（`@NotBlank`、`@Size`），Response 不放
- 每个接口方法一个 Request 类，不要复用。`CreateAgentRequest` 和 `UpdateAgentRequest` 是两个类
- Response 可以按粒度分：列表用精简版（`AgentListResponse`），详情用完整版（`AgentDetailResponse`）
- 禁止 DTO 之间互相继承。重复几个字段比继承层次清晰

### 6. Convertor 层

**职责：** Entity ↔ DTO 互转。

```java
@Mapper(componentModel = "spring")  // 用 MapStruct
public interface AgentConvertor {
    Agent toEntity(CreateAgentRequest request);
    AgentDetailResponse toDetailResponse(Agent agent);
    List<AgentListResponse> toListResponse(List<Agent> agents);
}
```

**规则：**
- 用 MapStruct 生成转换代码，不手写 getter/setter 转换
- Convertor 是无状态的，不注入 Service 或 Mapper
- 字段名不一致时用 `@Mapping` 注解标注

---

## 跨模块调用规则

### 规则 1：只能通过 Service 接口调用

```java
// ✓ 允许
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {
    private final ConversationService conversationService;  // 调接口
}

// ✗ 禁止
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {
    private final ConversationMapper conversationMapper;    // 跨模块直达数据层
    private final Conversation conversation;                // 跨模块直接用 Entity
}
```

### 规则 2：跨模块传递数据只允许用 DTO 或基本类型

```java
// ✓ 允许
ConversationDetailResponse conv = conversationService.getDetail(convId);

// ✗ 禁止
Conversation conv = conversationMapper.selectById(convId);
```

### 规则 3：禁止循环依赖

```
agent → conversation   ✓（单向依赖）
conversation → agent   ✗（形成循环）

如果 conversation 需要知道 agent 信息：
  方案 A：conversation 模块只存 agentId，由调用方（Controller/前端）分别取
  方案 B：如果必须联动，把联动逻辑上提到一个编排 Service
```

### 规则 4：模块间依赖关系只能是以下方向

```
conversation ──→ agent
conversation ──→ modelprovider
conversation ──→ knowledge
conversation ──→ mcp
workflow ──────→ agent
workflow ──────→ modelprovider
workflow ──────→ knowledge
workflow ──────→ mcp
agent ─────────→ modelprovider
agent ─────────→ knowledge
knowledge ────→ modelprovider        (Embedding 调用)
mcp ──────────→ (无外部依赖)

底层方向：上层业务 → 下层能力
禁止反向：下层不能依赖上层
```

### 规则 5：引擎类（engine/）不跨模块

```
ConversationEngine 只被 conversation 的 Service 调用
WorkflowEngine 只被 workflow 的 Service 调用

如果 workflow 需要发消息，它调 ConversationService，不调 ConversationEngine
```

---

## 依赖关系速查表

| 模块 | 可依赖 | 不可依赖 |
|------|--------|----------|
| agent | modelprovider, knowledge | conversation, workflow, mcp |
| conversation | agent, modelprovider, knowledge, mcp | workflow |
| workflow | agent, modelprovider, knowledge, mcp | conversation 的 engine |
| knowledge | modelprovider | agent, conversation, workflow, mcp |
| modelprovider | 无 | 所有其他模块 |
| mcp | 无 | 所有其他模块 |
| common | 无（纯被依赖） | 所有业务模块 |

---

## 命名规范

| 类别 | 命名格式 | 示例 |
|------|----------|------|
| Entity | 名词，单数 | `Agent`, `Conversation`, `WorkflowNode` |
| Mapper | Entity 名 + Mapper | `AgentMapper` |
| Service 接口 | Entity 名 + Service | `AgentService` |
| Service 实现 | Entity 名 + ServiceImpl | `AgentServiceImpl` |
| Controller | 模块名 + Controller | `AgentController` |
| Request DTO | 动作 + Entity 名 + Request | `CreateAgentRequest`, `UpdateAgentRequest` |
| Response DTO | Entity 名 + 场景 + Response | `AgentDetailResponse`, `AgentListResponse` |
| Convertor | Entity 名 + Convertor | `AgentConvertor` |
| 数据库表 | snake_case，复数不一定 | `agent`, `conversation`, `workflow_node` |
| API 路径 | /api/v1/模块名复数 | `/api/v1/agents`, `/api/v1/conversations` |
| 包名 | 全小写，单数 | `agent`, `conversation`, `modelprovider` |

---

## 统一响应格式

```java
@Data
public class R<T> {
    private int code;        // 0=成功, 非0=错误码
    private String message;  // 错误描述
    private T data;          // 业务数据

    public static <T> R<T> ok(T data) { ... }
    public static <T> R<T> fail(int code, String message) { ... }
}
```

分页响应：

```java
@Data
public class PageResult<T> {
    private List<T> items;
    private long total;
    private int page;
    private int pageSize;
}
```

---

## 异常处理

```java
// 业务异常：在 Service 层抛出
throw new BusinessException("Agent not found: " + id);

// 全局捕获：在 common/exception/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusiness(BusinessException e) {
        return R.fail(e.getCode(), e.getMessage());
    }
}
```

**规则：**
- Service 层抛 `BusinessException`，不抛 RuntimeException 或 IllegalArgumentException
- Controller 层不处理异常，全部交给 GlobalExceptionHandler
- 禁止在 catch 块里吞异常（空 catch）
- 禁止用异常做流程控制（比如用异常判断"记录是否存在"）
