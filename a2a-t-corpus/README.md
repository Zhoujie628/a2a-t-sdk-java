# a2a-t-corpus — A2A-T 一致性测试语料模块

## 这个模块是什么

`a2a-t-corpus` 是 A2A-T SDK 的**协议一致性测试语料模块**：用数据驱动的方式，验证 SDK 帮助上层 A2A 智能体完成两件核心事情的端到端正确性——

1. **生成符合规范的 Prompt**：从自然语言或结构化数据生成 Task-T 任务报文与 Negotiation-T 协商报文（propose / accept / reject / abort）；
2. **从交互 Prompt 中提取参数**：对端发来的报文经校验管线完成规则校验、语义校验与参数提取（含合并、缺参发现）。

模块为**纯测试模块**（无 `src/main`，jar 打包不进 BOM），在 reactor 中位于 `a2a-t-server` 之后、`a2a-t-sample` 之前，依赖 `a2a-t-client` / `a2a-t-server` / `a2a-t-negotiation`——因此能同时编排客户端 facade、服务端 facade 与协商内容服务，测试**跨模块的闭环行为**。这是它独立成模块的原因：单 API 用例与多步闭环场景共享同一套 harness 与业务事实，任何上游模块都覆盖不了全部协议扩展。

## 为什么采用"数据驱动语料"

- **用例即数据**：一个用例 = 一条自描述的 JSON 记录，新增用例零 Java 改动；
- **期望精确到键**：行为变更时红色 diff 直达 `expect.code: A != B`，修复 = 一次 JSON 编辑；
- **可查询**：用例 id 全局唯一（grep/jq 主键），`INDEX.md` 是机器生成的活文档（覆盖矩阵 + 逐用例索引）；
- **AI 可编写**：格式有正式 JSON Schema（`negotiation-cases/corpus-schema.json`），配套缺口驱动的编写流程。

## 目录结构

```
a2a-t-corpus/
├── README.md                                    ← 本文件
├── pom.xml
└── src/test/
    ├── java/net/openan/a2at/sdk/corpus/
    │   ├── NegotiationCaseLoader.java           语料加载（严格解析 + fail-fast 校验）
    │   ├── ScriptedNegotiationLlmClient.java    脚本化 LLM 桩（唯一测试接缝）
    │   ├── CaseEngine.java                      单用例引擎（API 分发 + 期望比对 + 契约断言）
    │   ├── ScenarioEngine.java                  多步场景引擎（跨步引用 + 流级断言）
    │   ├── TaskApiAssembler.java                task API 真实装配（builder 委托）
    │   ├── Contract.java                        行为契约注册表（12 个，P0/P1 分级）
    │   ├── *CorpusSuiteTest.java                四个 @TestFactory 套件（from-text / from-data / validate / scenario）
    │   ├── CorpusContractTest.java              元测试：覆盖完整性、双语 parity、评审门禁
    │   ├── CorpusSensitivitySelfTest.java       元元测试：翻转期望断言引擎必红（防橡皮图章）
    │   ├── property/                            jqwik 属性测试层（round-trip / 幂等 / 错误码分区 / 大参数合并）
    │   └── golden/                              golden fixture 生成器与一致性守卫
    └── resources/
        ├── negotiation-cases/
        │   ├── corpus-schema.json               语料格式的一手定义（JSON Schema）
        │   ├── INDEX.md                         自动生成的覆盖矩阵与用例索引（勿手编）
        │   ├── shared/                          共享 LLM payload（llm-responses.json）与 JSON Schema 变体（schemas.json）
        │   ├── from-text/                       fromText 家族用例（happy / 编程错误 / 模板解析 / 抽取失败 / 重试）
        │   ├── from-data/                       fromData 家族用例（happy / 编程错误）
        │   ├── validate/                        validate 家族用例（happy / 规则门 / 语义判定 / LLM 形态与重试 /
        │   │                                    合并与 schema / 错误码映射 / 对端报文漂移探针 / 编程错误）
        │   └── scenarios/                       E2E 场景（信息/目标/可行性协商流 + 边界流）
        └── golden/{zh-CN,en-US}/                golden fixture（模板渲染的基准输出，含 abort）
```

## 语料速览

当前规模：**151 个用例 + 18 个场景 = 238 条基记录**（双语展开后 226 个执行单元），业务域统一为**专线业务投诉诊断**（工作台智能体 ↔ SPN OMC 智能体的缺参协商闭环）。

### 单用例（case 记录）

```json
{
  "id": "FT-RETRY-02",
  "api": "generateAcceptFromText",
  "languages": ["zh-CN"],
  "priority": "P1",
  "tags": ["retry", "extract-failed"],
  "summary": "大模型第一次返回不可解析内容、第二次返回合法接受报文，重试后成功",
  "context": { "id": "3dbc13b5-...", "round": 2, "maxRounds": 5 },
  "templateUri": "Negotiation-T/information-negotiation/accept-reject/v1",
  "input": { "text": { "zh-CN": "我确认补充：接入端口名称 P533-……" } },
  "llm": { "maxAttempts": 3, "script": [ { "$fail": "non-json" }, { "$ref": "responses/extract.information.accept.full" } ] },
  "expect": { "outcome": "success", "llmCalls": 2, "promptTextEqualsGolden": "information_accept" }
}
```

要点：

- **LLM 脚本步三态**：`{"$ref": "responses/名称"}` 引共享 payload；`{"$fail": "标记"}` 注入失败（六种标记：runtime-exception / llm-error / null-response / blank-content / non-json / assertion）；字面 JSON 串内联。桩**耗尽即抛**（failOnOverconsumption），杜绝 repeat-last 掩盖错误期望。
- **`languages` 加载期展开**：`["zh-CN","en-US"]` 展开为两个执行单元（id 加 `/zh-CN`、`/en-US` 后缀），happy 用例强制双语 parity。
- **失败期望块**错误码是一等公民：`expect.code` / `expect.slotErrors`（槽+错误码精确匹配）/ `expect.llmCalls`（精确整数）。
- **差分断言**：happy 用例同时携带 `input.text` 与 `input.data`，引擎双跑断言 fromText == fromData == golden 三方相等。

### 场景（scenario 记录）

多步闭环按样例报文建模，如投诉诊断主链路：

```
step 1  工作台(A) generateTaskPromptFromText      用户投诉 → 任务报文
step 2  OMC(B)     validateTaskPromptAndDataFilling  校验任务报文 → 发现缺参（接入端口名称/投诉分类）
step 3  OMC(B)     generateNegotiationProposeFromData  缺参驱动发起协商（所需信息项带举例）
step 4  工作台(A)  generateNegotiationAcceptFromText    补参
step 5  OMC(B)     validateAcceptPromptAndDataFilling   提取补充参数
```

场景级因果断言：`expectFlow.missingParamsFilled` 保证**提取到的补参 == 发现的缺参**；另有 `terminalCondition`（accept/reject/abort/exhausted）、`roundsUsed`、`distinctMessages`。角色语义固定：**A = 工作台（客户端，任务发起/补数方），B = OMC（服务端，执行/要数方，协商发起方）**。

## 测试接缝设计（为什么测试可信）

- **单一脚本化接缝**：除 LLM 客户端外全部为**生产装配**——真实 builder、真实模板加载、真实规则门、真实渲染器。task API 经 `TaskApiAssembler` 走 `DefaultA2ATClientBuilder` / `DefaultA2ATServerBuilder`（与 `A2ATClient` / `A2ATServer` facade 内部同一条装配路径），脚本 LLM 通过 builder 的 `llmClient(...)` 注入口贯穿闭环，因此 `expect.llmCalls` 对全链路精确校准。
- **故障注入钩子**：`"inject": "failingTemplateLoader"` / `"failingSemanticValidator"` 映射到 builder 真实注入点，用于触发数据不可达的错误路径。
- **双层防呆**：
  - `CorpusContractTest`（元测试）：7 个协商错误码每个至少一个失败用例、5 条内部错误码映射、happy 双语 parity、task API 仅经场景步骤调用、闭环角色绑定等契约；
  - `CorpusSensitivitySelfTest`（元元测试）：自动翻转各类期望的样本值断言引擎**必须红**——防止"永远绿的橡皮图章引擎"，并区分引擎崩溃与断言失败。
- **属性层**（jqwik，固定种子）：fromData→validate round-trip 参数存活、生成幂等、每错误码的专门触发采样、50+ 键大参数合并（context 键冲突时 context 赢）、错误码分区（可重试 ⟺ {extract_failed, llm_infrastructure_error}）。

## 如何运行

```bash
mvn -pl a2a-t-corpus -am test                     # 本模块及其依赖（CI 会随 mvn clean verify 全量跑）
mvn -pl a2a-t-corpus test -Dtest=ValidateCorpusSuiteTest    # 单个套件
mvn -pl a2a-t-corpus test -Dcase.filter='FT-RETRY-*'        # glob 过滤用例 id
```

四个套件：`FromTextCorpusSuiteTest` / `FromDataCorpusSuiteTest` / `ValidateCorpusSuiteTest` / `ScenarioCorpusSuiteTest`。每条用例一个 DynamicTest，display name 带用例 id——CI 红色报告名即主键，`grep <id>` 直达唯一 JSON 文件。

配套工具（仓库根目录）：

```bash
python tools/corpus_index.py            # 重新生成 INDEX.md（语料变更后）
python tools/corpus_index.py --check    # 校验 INDEX 新鲜度
python tools/corpus_review.py export    # 生成业务评审材料（HTML 仪表盘 + 可批注 xlsx，技术字段翻译为业务中文）
python tools/corpus_review.py collect --marked <批注后的文件>   # 回读评审标记 → findings + review-status.json
```

## 如何扩展

### 新增一个用例

1. 打开 `negotiation-cases/corpus-schema.json`（格式一手定义）与同类别的现有 JSON 文件，模仿键序与风格；
2. 向对应类别文件 append 一个对象（id 按 `<家族前缀>-<类别>-<序号>` 全局唯一递增；`summary` 必须是面向业务的中文一句话）；
3. 共享 LLM payload 放 `shared/llm-responses.json` 并以 `{"$ref": "responses/名称"}` 引用；
4. 跑 `mvn -pl a2a-t-corpus test -Dcase.filter='<id>'` 验证，再重新生成 INDEX.md。

### 新增一个 API

在 `NegotiationApi` 枚举加条目并在 `CaseEngine` 分发；若属于新的装配面（参照 `TaskApiAssembler` 的 builder 委托写法）。

### 接入新的协议扩展（Task-T 独立用例 / Notification-T / Authorization-T）

本模块是四个 A2A-T 扩展共同的语料之家。接入步骤：

1. 在 `negotiation-cases/` 下按扩展新建语料目录（如 `task-cases/`），复用 `shared/` 体系或建扩展专属共享文件；
2. `NegotiationApi` 加该扩展的 API 条目 + `CaseEngine` 分发 + 必要的 Assembler（一律真实 builder 装配 + 脚本 LLM 注入，禁止镜像生产代码）；
3. 新增 `@TestFactory` 套件加载新目录，并在 `CorpusContractTest` 收紧对应覆盖契约；
4. `tools/corpus_index.py` / `corpus_review.py` 按目录扫描，加目录后刷新 INDEX 与评审投影即可。

## 约定与守卫

- 语料 JSON 由 loader **严格解析**（未知键、悬空 `$ref`、重复 id、期望块残缺一律加载期 fail-fast，报错点名文件+id+JSON 路径）——打错键名的红色正是守卫在工作；
- `INDEX.md` 为生成物，禁止手工编辑；
- golden fixture 由 `corpus.golden` 包的生成器产出（模板变更后重新生成）；
- 评审软门禁默认关闭：`-Dcorpus.review.gate=true` 开启"P0 用例评审通过率 100%"断言（需先经 `corpus_review.py` collect 产生 review-status.json）。
