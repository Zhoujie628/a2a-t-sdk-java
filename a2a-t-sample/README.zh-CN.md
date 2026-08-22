# a2a-t-sample

`a2a-t-sample` 是 A2A-T Java SDK 的示例模块，包含客户端与服务端两个可直接运行的入口。

当前示例基于 `a2a-java v1.0.0.Beta1` 运行真实的 A2A `HTTP+JSON/REST` 链路：
- `a2a-t-client` 仅用于生成结构化 prompt
- `a2a-t-server` 仅用于校验结构化 prompt

## 入口类

- 客户端：`net.openan.a2at.sample.subscribe_incident.client.ClientSampleMain`
- 服务端：`net.openan.a2at.sample.subscribe_incident.server.ServerSampleMain`

## 模块内资源

- 客户端环境模板：`sample/subscribe_incident/client/client.env`
- 服务端环境模板：`sample/subscribe_incident/server/server.env`
- 客户端场景输入：`sample/subscribe_incident/client/scenario.json`

## 客户端启动

1. 修改仓库根目录下的 `client.env`，补充可用的 `A2AT_LLM_API_KEY`
2. 如需修改默认请求内容，可编辑 `sample/subscribe_incident/client/scenario.json`
3. 启动客户端：

```bash
java @a2a-t-sample/target/client.javaargs.txt
```

如果不传参数，`ClientSampleMain` 会回退到包内的 `sample/subscribe_incident/client/client.env`。

## 服务端启动

1. 修改仓库根目录下的 `server.env`，补充可用的 `A2AT_LLM_API_KEY`
2. 启动服务端：

```bash
java @a2a-t-sample/target/server.javaargs.txt
```

如果不传参数，`ServerSampleMain` 会回退到包内的 `sample/subscribe_incident/server/server.env`。

## Git Bash 本地调试

先编译打包：

```bash
mvn "-Dmaven.repo.local=.mvn/repository" -pl a2a-t-sample -am -DskipTests package
```

启动服务端：

```bash
java @a2a-t-sample/target/server.javaargs.txt
```

另开一个窗口启动客户端：

```bash
java @a2a-t-sample/target/client.javaargs.txt
```

## Task-T 准确率验证样例

`TaskTDemoMain` 是一个不依赖注册中心、不发送真实 A2A 请求的本地闭环样例：客户端 facade 生成结构化
`Task-T` prompt，服务端 facade 校验并重新提取参数，再与样本的期望值比对，分别统计
`generateTaskPromptFromText` 与 `generateTaskPromptFromDataWithSchema` 两个客户端 API 的准确率。

入口类：`net.openan.a2at.sample.task_t.TaskTDemoMain`

样例内容：

- 模板：`Task-T/network-layer/private-line-complaint/v1`（`StandardTemplates.PRIVATE_LINE_COMPLAINT`，传输专线业务投诉诊断）
- 客户端结构化入参：`data` 使用**英文业务字段**（客户端 key），不直接复用模板槽位结构；由客户端将业务字段
  渲染映射进模板槽位
- 两端 key 分离（验证 SDK 适配）：客户端 key（`portName`/`complaintScenario`/`faultStartTime`/`ticketNo`/
  `faultDetailText`）与服务端 key（`accessPort`/`bizScenario`/`faultTime`/`eventSerialNo`/`faultDetail`）语义近似但
  **字段名不一致** —— 服务端按自己的 key 从渲染后的 prompt 提取参数，评估（ground truth）也按服务端 key 记分；
  客户端发什么 key、服务端收什么 key 互不假设，闭环检验 a2a-t SDK 的跨 key 适配能力
- 两端 schema：客户端面向 prompt 生成（字段语义、格式示例、取值范围引导），服务端面向提交校验
  （标准 JSON Schema，必填约束、取值限定），各自独立维护
- 样本：共 **12 组**（`private-line-complaint-samples.json` 数据源，可自行替换为自己的场景）——6 组**简短口语化自然
  语言文本**（字段信息以叙述方式出现而非罗列，验证 SDK 解析自然语言的能力）+ 6 组结构化 data+schema
  （结构化入参使用英文业务字段，不直接复用模板槽位结构）；完整用例清单见
  [`task-t-test-cases.md`](task-t-test-cases.md)
- 闭环：`A2ATClient` 生成 → `A2ATServer#validateAndFillingTaskData` 校验提取 → 与 ground truth 比对
- 评估：字段级准确率 = 命中期望字段数 / 期望字段总数；样本级通过率 = 全部字段命中的样本数 / 样本总数

命中规则：去掉空白并统一小写后，提取值与期望值完全相同或互相包含即为命中；提取值缺失视为未命中。

### 运行要求

1. 配置 LLM 环境：直接编辑包内模板 `sample/task_t/client.env` 补充可用的 `A2AT_LLM_API_KEY`（配置
   `A2AT_LLM_PROVIDER=openai`、`A2AT_LLM_BASE_URL`、`A2AT_LLM_MODEL`，每个样本的服务端语义校验各消耗一次
   LLM 调用）；也可在仓库根目录放置自己的 `client.env`。
2. 先编译打包：

```bash
mvn -pl a2a-t-sample -am -DskipTests package
```

### 启动

env 文件解析顺序：命令行第一参数（任意路径，例如 `server.env`）→ 工作目录下的 `client.env`（仓库根，且必须含
非空的 `A2AT_LLM_PROVIDER`/`A2AT_LLM_MODEL`/`A2AT_LLM_API_KEY`，否则跳过）→ 包内模板
`sample/task_t/client.env`。不传参数时会自动跳过仓库根遗留的空配置、回退到包内模板。

PowerShell：

```powershell
$cp = "a2a-t-sample/target/a2a-t-sample-1.0.0.jar;" + (Get-Content a2a-t-sample/target/sample-runtime-classpath.txt)
java -cp $cp net.openan.a2at.sample.task_t.TaskTDemoMain
```

Git Bash：

```bash
java -cp "a2a-t-sample/target/a2a-t-sample-1.0.0.jar:$(cat a2a-t-sample/target/sample-runtime-classpath.txt)" \
  net.openan.a2at.sample.task_t.TaskTDemoMain
```

也可显式传入任意 `.env` 路径（例如 `client.env`）。运行结束后会打印每个样本的出入参、字段级命中明细，
以及两个客户端 API 各自的字段准确率与样本通过率。

> 说明：`a2a-t-sample-1.0.0.jar` 假设模块版本为 `1.0.0`，若仓库版本已更新请以实际 jar 为准。
> 该样例为演示目的，期望值取各槽位的关键事实，命中规则较宽松（相同或互相包含）；如需严谨评测，
> 可自行扩充样本并收紧期望值。
