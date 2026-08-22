 # A2A-T 协商样例设计与实现说明
 
 > 记录目标、架构、依赖、注意事项、代码索引、运行方式，便于理解和维护本样例。
 
 ---
 
 ## 一、任务目标
 
 基于 `a2a-t-sdk-java` 的协商（Negotiation-T）SDK 能力，构建可运行的端到端 demo，覆盖文档 §7.3/§7.4 定义的"传输专线业务投诉诊断"4 报文协商流程，并输出 fromData / fromText 两种协商生成方式的 API 验证样例。
 
 ### 分工
 
 | 方式 | API | 特点 |
|---|---|---|
| fromData（结构化数据，规则生成） | `generateNegotiationProposePromptFromData` / `...Accept...` / `...Reject...` | 协商报文生成不调 LLM，确定性 |
| fromText（自然语言，LLM 抽取） | `generateNegotiationProposePromptFromText` / `...Accept...` / `...Reject...` | 协商报文生成需 LLM |
 
 两种方式的**唯一差异**是协商报文的生成方式（规则 vs LLM）。其他环节（Task-T 报文生成、参数校验、状态机）完全一致，该用 LLM 的地方都用 LLM。**整个 demo 始终需要真实 LLM API key**：fromData 只省掉协商报文生成这一个环节的 LLM 调用，Task-T 槽位提取与语义校验仍走 LLM。
 
 ---
 
 ## 二、4 报文流转（文档 §7.3/§7.4）
 
 | 报文 | 方向 | 扩展 | SDK API | 内容 | 状态 |
 |---|---|---|---|---|---|
 | 1 | client→server | Task-T | `generateTaskPromptFromDataWithSchema` | 专线投诉诊断任务，参数缺失（接入端口名称空） | → |
 | 2 | server→client | Negotiation-T | `validateAndFillingTaskData` 校验→`ContentValidationException.errors()` 提取缺失项→`NegotiationStrategy.generatePropose` | 信息协商请求，动态列出缺失参数 | INPUT_REQUIRED |
 | 3 | client→server | Task-T + Negotiation-T | `generateTaskPromptFromDataWithSchema`(补齐) + `NegotiationStrategy.generateAccept` | 参数补齐 + 协商接受 | → |
 | 4 | server→client | Task-T | `validateAndFillingTaskData` 校验通过→`DiagnosisService.diagnose(params)` | 诊断结果（从提取参数动态生成） | COMPLETED |
 
 全程走真实 a2a-java HTTP A2A（`message:send`）。
 
 ---
 
 ## 三、核心设计：策略模式隔离 fromData / fromText
 
 ```
 NegotiationStrategy（接口）
   ├── FromDataStrategy  → 构造类型化 record → generateXxxFromData（规则，不调 LLM）
   └── FromTextStrategy   → 转自然语言文本 → generateXxxFromText（LLM 抽取 + 渲染）
 ```
 
 - `NegotiationDemoApp` 接受 `--fromText` 参数，选择策略注入 server 和 client
 - server `NegotiationAgentExecutor` 和 client `NegotiationClient` 都持有一个 `NegotiationStrategy` 实例
 - 协商报文生成时调用 `strategy.generatePropose(...)` / `strategy.generateAccept(...)`
 - 两种策略的差异仅在"怎么把缺失/补齐参数转成 API 输入"（record vs 文本），其余逻辑完全共享
 
 ---
 
 ## 四、不写死——全动态生成
 
 | 环节 | 旧做法（写死） | 新做法（动态） |
 |---|---|---|
 | 参数缺失检测 | `hasMissingParams` 轻量字符串匹配 | `validateAndFillingTaskData`（SDK LLM 语义校验） |
 | 缺失参数列表 | `InfoProposeContent` 硬编码 | 从 `ContentValidationException.errors()` 动态提取 slotName |
 | accept 内容 | `InfoEndingContent` 硬编码 | 从 `ScenarioData.filledParams()` 动态构造 NegotiationItem 列表 |
 | 诊断结果 | `DiagnosisService.diagnose()` 固定文本 | `DiagnosisService.diagnose(FilledParamData)` 从提取参数动态生成 |
 | 协商报文生成 | 只用 fromData | 策略模式，fromData/fromText 可切换 |
 
 支持泛化输入：换一个 Task-T 输入（缺不同参数），协商内容自动适配。
 
 ---
 
 ## 五、依赖文档
 
 | 文档 | 路径 | 用途 |
 |---|---|---|
 | A2A-T 协商 API 文档（中） | `docs/zh/A2A-T-Negotiation-API-Reference.md` | 协商三层 API（状态机/内容生成/校验）、类型化内容模型、模板 URI、异常码 |
 | A2A-T 协商 API 文档（英） | `docs/en/A2A-T-Negotiation-API-Reference.md` | 同上英文版 |
 | A2A-T SDK API 综合 | `docs/zh/A2A-T-SDK-API-Reference.md` | 全局 API 参考（协商分册在此基础上） |
 | Task-T 模板资源 | `a2a-t-resources/src/main/resources/prompt_resources/templates/Task-T/network-layer/private-line-complaint/v1/zh-CN/template.md` | 专线投诉诊断 Task-T 模板 |
 | Task-T slot schema | `a2a-t-resources/src/main/resources/prompt_resources/slots/Task-T/network-layer/private-line-complaint/v1/zh-CN/slot.json` | Task-T 参数 schema（任务对象/任务上下文） |
 | Negotiation-T 模板 | `a2a-t-resources/.../templates/Negotiation-T/` | 信息/目标/可行性协商 propose + accept-reject 模板 |
 | content_validation prompt | `a2a-t-resources/.../prompts/content_validation/zh-CN/system.md` + `user.md` | Task-T 服务端语义校验的 LLM prompt |
 
 ### SDK 关键类索引
 
 | 类 | 模块 | 作用 |
 |---|---|---|
 | `A2ATClient` | a2a-t-client | 客户端 facade（fromData/fromText 生成 + validate + 状态机） |
 | `A2ATServer` | a2a-t-server | 服务端 facade（同上，额外 prompt compliance） |
 | `NegotiationContext` (content 层) | a2a-t-negotiation | 协商会话上下文（id/round/maxRounds） |
 | `NegotiationContext` (types.model 层) | a2a-t-negotiation | 状态机上下文（type/id/round/status） |
 | `NegotiationProposeData` / `NegotiationEndingData` | a2a-t-negotiation | fromData 输入 |
 | `InfoProposeContent` / `TargetProposeContent` / `FeasibilityProposeContent` | a2a-t-negotiation | 三种协商类型的 propose 内容 |
 | `InfoEndingContent` / `TargetEndingContent` / `FeasibilityEndingContent` | a2a-t-negotiation | 三种协商类型的 ending 内容 |
 | `MetadataContent` | a2a-t-core | 生成输出（templateUri + promptText + extensionUri） |
 | `FilledParamData` | a2a-t-core | 校验参数提取结果 |
 | `ContentValidationException` | a2a-t-core | 校验失败异常（携带 `errors()` 即 `List<SlotValidationError>`） |
 | `NegotiationPayloadMapper` | a2a-t-negotiation | payload 序列化/反序列化 |
 | `EmbeddedA2AHttpServer` | a2a-t-sample (subscribe_incident) | 嵌入式 HTTP A2A 服务器 |
 | `DefaultRequestHandler` / `AgentExecutor` / `AgentEmitter` | a2a-java SDK (org.a2aproject.sdk) | A2A 服务端请求处理 |
 | `RestTransport` / `AgentCard` / `Message` / `Task` | a2a-java SDK | A2A 客户端传输 + 数据模型 |
 
 ---
 
 ## 六、代码索引
 
 ### 端到端 demo（4 报文）
 
 | 文件 | 作用 |
 |---|---|
 | `negotiation/NegotiationDemoApp.java` | 入口：启动 HTTP server + 跑 client，`--fromText` 切换策略 |
 | `negotiation/client/NegotiationClient.java` | 4 报文编排：Task-T 缺失→收协商→补齐→收诊断 |
 | `negotiation/server/NegotiationAgentExecutor.java` | AgentExecutor：validateAndFillingTaskData→缺失检测→协商请求→诊断 |
 | `negotiation/server/NegotiationServerRuntime.java` | HTTP server + DefaultRequestHandler + AgentCard 装配 |
 | `negotiation/server/DiagnosisService.java` | 从 FilledParamData 动态生成诊断结果 |
 
 ### 策略层（fromData / fromText 差异隔离）
 
 | 文件 | 作用 |
 |---|---|
 | `negotiation/shared/NegotiationStrategy.java` | 策略接口（generatePropose / generateAccept / generateAcceptServer） |
 | `negotiation/shared/FromDataStrategy.java` | fromData 实现（record + fromData API，规则） |
 | `negotiation/shared/FromTextStrategy.java` | fromText 实现（文本 + fromText API，LLM） |
 
 ### 公共层
 
 | 文件 | 作用 |
 |---|---|
 | `negotiation/shared/NegotiationMessage.java` | A2A metadata 桥接（Task-T/Negotiation-T prompt + context 序列化） |
 | `negotiation/shared/DemoConstants.java` | 扩展 URI、模板 URI 常量 |
 | `negotiation/shared/ScenarioData.java` | 场景数据加载器（从 `scenario.json` 读取 slot schema + 参数缺失/补齐数据） |
 | `negotiation/shared/NegotiationSampleSupport.java` | fromData/fromText 样例公共辅助（SessionId、URI 常量、summary） |
 
 ### API 验证样例（3 类型 × 3 阶段 = 9 用例）
 
 | 文件 | 作用 |
 |---|---|
 | `negotiation/fromdata/FromDataNegotiationSample.java` | 9 用例，fromData API，报文生成确定性 |
 | `negotiation/fromtext/FromTextNegotiationSample.java` | 9 用例，fromText API，报文生成含 LLM 抽取 |
 
 ### 文档
 
 | 文件 | 作用 |
 |---|---|
 | `docs/zh/A2A-T-Negotiation-API-Reference.md` | 协商 API 文档（中） |
 | `docs/en/A2A-T-Negotiation-API-Reference.md` | 协商 API 文档（英） |
 | `a2a-t-sample/docs/Negotiation-Sample-Design.md` | 本文档 |
 
 ---
 
 ## 七、SDK bug 修复（a2a-t-prompt 模块）
 
 构建 demo 时发现并修复了 `a2a-t-prompt` 模块两个 bug：
 
 ### Bug 1：`DefaultSemanticValidator.loadPrompt` 漏 `.md`
 
 - 文件：`a2a-t-prompt/.../DefaultSemanticValidator.java`
 - 问题：`loadPrompt("content_validation", language, "system")` 的 fileName 不带 `.md`，`LocalFileAccess` 找不到 `system.md` 文件
 - 对比：Negotiation 模块的 `DefaultNegotiationSemanticValidator` 传的是 `"system.md"`（正确）
 - 修复：改为 `"system.md"` / `"user.md"`
 
 ### Bug 2：`parseParams` 的 `Map.copyOf` 对 null value NPE
 
 - 文件：`a2a-t-prompt/.../DefaultSemanticValidator.java`
 - 问题：LLM 语义校验返回的 params map 可能含 null value，`Map.copyOf` 不允许 null 抛 NPE
 - 修复：过滤 null value 后再 `Map.copyOf`
 
 ---
 
 ## 八、运行方式
 
 ### 前置条件
 
 - JDK 17+
- `mvn clean install -DskipTests`（全量构建，errorprone 在 JDK17 下正常工作，需联网模式）
 - 含真实 LLM API key 的 `.env` 文件（参考 `a2a-t-sample/src/main/resources/sample/negotiation/negotiation.env` 模板，填入 `A2AT_LLM_API_KEY`）
 
 ### 4 报文端到端 demo
 
 ```bash
 # fromData 策略（协商报文规则生成，不调 LLM；Task-T 槽位提取/语义校验仍需 LLM key）
 java @a2a-t-sample/target/negotiation.javaargs.txt /path/to/.env
 
 # fromText 策略（协商报文 LLM 生成）
 java @a2a-t-sample/target/negotiation.javaargs.txt --fromText /path/to/.env
 ```
 
 ### API 验证样例
 
 ```bash
 # fromData 9 用例（报文生成不调 LLM，但 A2ATClient 构造仍需有效的 LLM key）
 java @a2a-t-sample/target/fromdata.javaargs.txt /path/to/.env
 
 # fromText 9 用例（LLM 抽取，需 LLM key）
 java @a2a-t-sample/target/fromtext.javaargs.txt /path/to/.env
 ```
 
 ---
 
 ## 九、注意事项与已知限制
 
 1. **errorprone 与 JDK17**：`old_jdk_support` profile（`<jdk>[17, 21]</jdk>`）自动降级 errorprone 到 2.42.0。离线模式（`-o`）会因插件 jar 不完整导致 `ServiceLoader defineClass` 失败，必须联网构建。
 
 2. **模板 URI 格式**：正确格式是 `Negotiation-T/{type-segment}/{phase-segment}/v1`（v1 在末段）。文档之前写的 `Negotiation-T/v1/{type}/{phase}` 是错的，已修正。
 
 3. **两套 NegotiationContext**：`content.NegotiationContext`（id/round/maxRounds，用于内容生成）和 `types.model.NegotiationContext`（type/id/round/status，用于状态机），不可混用。
 
 4. **AgentInterface 构造函数**：4 参数版 `(protocolBinding, url, tenant, protocolVersion)`，tenant 必须传空字符串（不能省略），否则 `buildBaseUrl` 会把 protocolVersion 当 tenant 追加到 URL。
 
 5. **message:send 返回 Task**：非流式 `message:send` 返回的 `EventKind` 是 `Task`（不是 `Message`），回复在 `Task.history()` 或 `Task.artifacts()` 里，需要根据场景提取。
 
 6. **DiagnosisService 是 mock**：诊断结果从 `FilledParamData` 动态生成，但不是真实网管诊断。真实场景应替换为 EMS/NMS 北向 API 调用。
 
 7. **fromText 报文生成需要 LLM key**：`FromTextNegotiationSample` 和 `--fromText` 模式的协商报文生成含一步 LLM 抽取。fromData 样例的报文生成虽不调 LLM，但 `A2ATClient` 构造即校验 `A2AT_LLM_API_KEY` 非空，同样需要有效 key。
 
 8. **content_validation 资源**：Task-T 服务端语义校验需要 `content_validation` prompt 资源（system.md/user.md）。`local_file` 模式指向 `a2a-t-resources/src/main/resources/prompt_resources`；`classpath` 模式需 a2a-t-resources jar 在 classpath 上。
 
 9. **Unicode 转义已修复**：之前因 PowerShell 编码问题用了 `\uXXXX` 转义序列，已全部替换为 UTF-8 中文字符，符合开源规范。
 
 10. **NegotiationServerRuntime 未调 receiveNegotiation**：当参数缺失时跳过 `receiveNegotiation`（因为 InformationNegotiation handler 会调用 compliance checker 拒绝不完整 Task-T）。直接从 `ContentValidationException.errors()` 提取缺失项后生成协商请求。
 
 ---
 
 ## 十、fromData / fromText 验证覆盖矩阵
 
 | 类型 | propose | accept | reject |
 |---|---|---|---|
 | 信息协商（information） | ✅ | ✅ | ✅ |
 | 目标协商（target） | ✅ | ✅ | ✅ |
 | 可行性协商（feasibility） | ✅ | ✅ | ✅ |
 
 fromData 和 fromText 各 9 个用例，全部通过。
