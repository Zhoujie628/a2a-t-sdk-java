# A2A-T 协商样例（Negotiation Sample）

本样例演示 A2A-T 协商扩展（Negotiation-T）的完整业务闭环：客户端提交参数不全的任务请求，服务端通过协商机制向客户端索要缺失参数，客户端补充后任务继续执行。样例覆盖协商的两个核心生成通道（结构化数据 / 自然语言）与两种 A2A 传输端点（流式 / 阻塞），全部基于真实 HTTP 交互，无 mock 桩。

---

## 一、协商场景

样例使用"传输专线投诉诊断"业务场景。客户端发起专线投诉诊断任务时遗漏了标识专线业务对象的必选信息（专线名称 / 接入端口名称 / 接入端口资源 ID，三者提供其一），服务端无法定位故障对象，于是发起一轮信息协商：

| 报文 | 方向 | 扩展 | 任务状态 | 说明 |
|---|---|---|---|---|
| 1 | client → server | Task-T | — | 投诉诊断请求，任务对象参数为空 |
| 2 | server → client | Negotiation-T | INPUT_REQUIRED | 服务端检测到参数缺失，发起信息协商请求，动态列出缺失项 |
| 3 | client → server | Task-T + Negotiation-T | — | 客户端补齐参数并附协商接受报文 |
| 4 | server → client | Task-T | COMPLETED | 服务端复验通过，基于提取参数输出诊断结果 |

协商触发完全由数据驱动：服务端对收到的任意 Task-T 请求执行语义校验，校验报告哪些参数缺失，协商请求的内容即由校验结果生成。更换任务输入（缺失不同参数、甚至不缺参数）无需改动任何代码——不缺参数时流程直接跳过协商进入执行。

## 二、协商报文的两种生成通道

协商报文的生成提供两个等价通道，由策略模式隔离差异：

```
NegotiationStrategy（接口）
  ├── FromDataStrategy  → 类型化内容 record → generateXxxFromData（模板渲染，确定性）
  └── FromTextStrategy  → 自然语言文本    → generateXxxFromText（LLM 结构化抽取 + 模板渲染）
```

两个通道仅在"缺失/补齐参数如何进入生成 API"这一步不同：fromData 通道把参数组装成类型化内容记录（`InformationProposeContent` 等），由 SDK 直接渲染模板；fromText 通道把参数组织成自然语言语句，由 SDK 先做一步 LLM 结构化抽取再渲染。Task-T 报文生成、参数语义校验、协商状态机等其余环节两个通道完全共享。

入口通过命令行参数选择通道：默认 fromData，`--fromText` 切换为自然语言通道。

## 三、传输端点

客户端按服��端 AgentCard 声明的能力选择 A2A 传输端点：

- 服务端声明 `streaming=true` 时走 `message:stream`，客户端聚合流式事件（任务终态或末条 agent 消息）得到回复；
- 否则走 `message:send` 阻塞式往返。

入口默认优先流式；`--no-stream` 参数强制阻塞端点，用于覆盖服务端不支持流式的场景。两条路径产出统一的回复载体，协商流程逻辑与端点无关。

## 四、代码结构

```
negotiation/
├── NegotiationDemoApp.java          # 入口：装配嵌入式 HTTP server + 驱动客户端流程
├── client/
│   └── NegotiationClient.java       # 4 报文编排 + 传输端点选择与流事件聚合
├── server/
│   ├── NegotiationAgentExecutor.java # 接收请求 → 语义校验 → 缺失检测 → 协商/执行分流
│   ├── NegotiationServerRuntime.java # AgentCard（声明 Task-T/Negotiation-T 扩展与流式能力）+ HTTP 装配
│   └── DiagnosisService.java        # 从校验提取的参数生成诊断结果
├── shared/
│   ├── NegotiationStrategy.java     # 协商报文生成策略接口
│   ├── FromDataStrategy.java        # 结构化数据通道
│   ├── FromTextStrategy.java        # 自然语言通道
│   ├── NegotiationMessage.java      # A2A metadata 桥接（扩展 prompt + 协商上下文序列化）
│   ├── DemoConstants.java           # 扩展 URI / 元数据键常量
│   ├── ScenarioData.java            # scenario.json 加载器
│   └── NegotiationSampleSupport.java # 3×3 API 样例公共辅助
├── fromdata/
│   └── FromDataNegotiationSample.java  # 信息/目标/可行性 × 提议/接受/拒绝，共 9 个生成用例
└── fromtext/
    └── FromTextNegotiationSample.java  # 同上 9 个用例，自然语言输入
```

### 服务端处理流程

`NegotiationAgentExecutor` 对每个入站请求执行：

1. 从 `Message.metadata` 提取 Task-T prompt；
2. 调用 `A2ATServer.validateAndFillingTaskData` 做语义校验与参数提取；
3. 校验通过且无缺失参数 → 生成诊断结果 artifact，任务转 COMPLETED；
4. 校验拒绝（参数缺失）→ 从 `ContentValidationException.errors()` 提取缺失槽位，经策略生成 Negotiation-T 信息协商请求，任务转 INPUT_REQUIRED。

步骤 3、4 之间没有任何与特定参数绑定的逻辑——缺失项名称、数量、提示文案均来自校验结果与场景配置。

### 场景数据外部化

场景的全部实例化值集中在 `src/main/resources/sample/negotiation/scenario.json`：Task-T 槽位 schema（含槽位描述与示例）、参数缺失/补齐两份输入、协商文案模板（`{slot}`/`{description}`/`{params}` 占位符）、诊断结果行模板、3×3 API 样例的全部用例数据。Java 代码只保留通用规则（槽位遍历、编号列表拼装、占位符替换），源码中不含任何场景实例化字符串。更换演示场景只需编辑该文件。

## 五、运行

前置条件：

- JDK 17+；
- `mvn clean install -DskipTests` 全量构建（需联网）；
- 含有效 `A2AT_LLM_API_KEY` 的 `.env` 文件（模板：`sample/negotiation/negotiation.env`）。

```bash
# 端到端 demo（默认：fromData 通道 + 流式优先）
java @a2a-t-sample/target/negotiation.javaargs.txt /path/to/.env

# 自然语言通道
java @a2a-t-sample/target/negotiation.javaargs.txt --fromText /path/to/.env

# 强制阻塞端点（覆盖 message:send 场景）
java @a2a-t-sample/target/negotiation.javaargs.txt --no-stream /path/to/.env

# 3×3 API 生成用例（结构化数据通道 / 自然语言通道）
java @a2a-t-sample/target/fromdata.javaargs.txt /path/to/.env
java @a2a-t-sample/target/fromtext.javaargs.txt /path/to/.env
```

组合 `--fromText` 与 `--no-stream` 可覆盖全部四种通道 × 端点组合。

## 六、LLM 依赖说明

`A2AT_LLM_API_KEY` 在所有运行模式下必填。fromData 通道仅省去协商报文渲染这一步的 LLM 调用；Task-T 槽位抽取与服务端语义校验始终依赖 LLM。缺失 key 时启动即失败并给出指引。

## 七、覆盖矩阵

| 维度 | 覆盖 |
|---|---|
| 协商类型 | 信息协商（端到端流程）；信息 / 目标 / 可行性（3×3 生成用例） |
| 协商阶段 | 提议 / 接受 / 拒绝 |
| 报文生成通道 | fromData（结构化）/ fromText（自然语言） |
| 传输端点 | message:stream / message:send |
| 参数缺失形态 | 单参数缺失、多参数缺失、值模糊、字段错位（见 scenario.json，可扩展） |

## 八、已知限制

1. `DiagnosisService` 输出的是基于校验参数生成的文本结果，非真实网管诊断；生产场景应替换为 EMS/NMS 北向接口调用。
2. 服务端在参数缺失分支跳过 `receiveNegotiation`（信息协商 handler 的合规检查会拒绝不完整的 Task-T），缺失项直接取自语义校验错误列表。
3. 协商状态机的两套 `NegotiationContext`（content 层与 types.model 层）职责不同，不可混用：前者注入渲染内容，后者驱动状态推进。

## 九、相关文档

- 协商 API 参考：`docs/zh/A2A-T-Negotiation-API-Reference.md`（英文版 `docs/en/`）
- SDK 整体 API 参考：`docs/zh/A2A-T-SDK-API-Reference.md`
- 场景输入定义：`src/main/resources/sample/negotiation/scenario.json`
