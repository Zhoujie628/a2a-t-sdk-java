# a2a-t-sample

`a2a-t-sample` 是 A2A-T Java SDK 的示例模块，包含客户端与服务端两个可直接运行的入口。

## 订阅事件 Sample

该示例基于 `a2a-java v1.0.0.Beta1` 运行真实的 A2A `HTTP+JSON/REST` 链路：

- `a2a-t-client` 仅用于生成结构化 prompt
- `a2a-t-server` 仅用于校验结构化 prompt

### 入口类

- 客户端：`net.openan.a2at.sample.subscribe_incident.client.ClientSampleMain`
- 服务端：`net.openan.a2at.sample.subscribe_incident.server.ServerSampleMain`

### 模块内资源

- 客户端环境模板：`sample/subscribe_incident/client/client.env`
- 服务端环境模板：`sample/subscribe_incident/server/server.env`
- 客户端场景输入：`sample/subscribe_incident/client/scenario.json`

### 客户端启动

1. 修改仓库根目录下的 `client.env`，补充可用的 `A2AT_LLM_API_KEY`
2. 如需修改默认请求内容，可编辑 `sample/subscribe_incident/client/scenario.json`
3. 启动客户端：

```bash
java @a2a-t-sample/target/client.javaargs.txt
```

如果不传参数，`ClientSampleMain` 会回退到包内的 `sample/subscribe_incident/client/client.env`。

### 服务端启动

1. 修改仓库根目录下的 `server.env`，补充可用的 `A2AT_LLM_API_KEY`
2. 启动服务端：

```bash
java @a2a-t-sample/target/server.javaargs.txt
```

如果不传参数，`ServerSampleMain` 会回退到包内的 `sample/subscribe_incident/server/server.env`。

## 传输专线投诉信息协商 Sample

该示例只覆盖 Negotiation-T：Propose 的自然语言生成与校验/提参，以及 Accept 或 Reject 的自然语言生成与校验/提参。生成结果通过 A2A Message 和 Artifact metadata 传输。

相关文档：

- [六个接口的逐项调用示例](NEGOTIATION_API_EXAMPLES.zh-CN.md)
- [Sample 开发与设计说明](NEGOTIATION_SAMPLE_DEVELOPMENT.zh-CN.md)
- [Qwen3-32B 的 100 用例批量验证](NEGOTIATION_QWEN_EVALUATION.zh-CN.md)

调用方 schema 根据专线投诉场景分别定义：Propose 和 Accept 提取 `access_port_name`、`complaint_category`，Reject 提取 `rejection_reason`。`items`、`relationship`、`conclusion` 等协商模板结构由 SDK 内部处理。

默认使用本地 schema 分派 Mock LLM，无需访问模型服务。

### 构建

在仓库根目录执行：

```powershell
mvn -pl a2a-t-sample -am test
mvn -pl a2a-t-sample -am -DskipTests package
```

### 本地运行

第一个终端启动 Accept 分支服务端：

```powershell
$env:A2AT_SAMPLE_NEGOTIATION_DECISION = "accept"
java @a2a-t-sample/target/negotiation-server.javaargs.txt
```

第二个终端启动客户端：

```powershell
java @a2a-t-sample/target/negotiation-client.javaargs.txt
```

Reject 分支将服务端环境变量改为 `reject` 后重启，再运行客户端。服务端默认端口为 `8010`。

目标网络使用 Qwen3-32B 时，将 Client 和 Server 两个 env 中的 LLM 配置替换为：

```properties
A2AT_LLM_PROVIDER=openai
A2AT_LLM_MODEL=qwen3-32b
A2AT_LLM_BASE_URL=http://71.77.65.40:8008/v1
A2AT_LLM_API_KEY=not-needed
A2AT_LLM_TEMPERATURE=0
A2AT_LLM_TIMEOUT_SECONDS=60
A2AT_LLM_DISABLE_SYSTEM_PROXY=true
```

仓库已提供可直接用于东莞绿区的 `sample/private-line-complaint-negotiation/qwen.env`。构建后，可显式将该文件作为两个入口的第一个参数；无需修改默认 Mock 配置：

```powershell
$env:A2AT_SAMPLE_NEGOTIATION_DECISION = "accept"
java @a2a-t-sample/target/negotiation-server.javaargs.txt a2a-t-sample/src/main/resources/sample/private-line-complaint-negotiation/qwen.env
```

```powershell
java @a2a-t-sample/target/negotiation-client.javaargs.txt a2a-t-sample/src/main/resources/sample/private-line-complaint-negotiation/qwen.env
```

需要批量验证三组自然语言生成接口及对应校验/提参接口时，运行以下命令。它会执行 100 条用例、生成汇总报告和逐调用 JSONL 过程日志；日志可用于区分生成、校验或 Sample 入参问题：

```powershell
java @a2a-t-sample/target/negotiation-qwen-evaluation.javaargs.txt `
  a2a-t-sample/src/main/resources/sample/private-line-complaint-negotiation/qwen.env `
  a2a-t-sample/target/negotiation-qwen-report.json `
  a2a-t-sample/target/negotiation-qwen-process.jsonl
```

完整执行前提和日志判读规则见 [Qwen3-32B 的 100 用例批量验证](NEGOTIATION_QWEN_EVALUATION.zh-CN.md)。
