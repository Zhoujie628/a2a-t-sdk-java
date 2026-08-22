# a2a-t-sample

`a2a-t-sample` 是 A2A-T Java SDK 的示例模块，每组示例包含客户端与服务端两个入口，基于
`a2a-java v1.0.0.Beta1` 运行真实的 A2A `HTTP+JSON/REST` 链路：

- `a2a-t-client` 仅用于生成结构化 prompt
- `a2a-t-server` 仅用于校验结构化 prompt

## 快速开始（推荐：统一启动器）

从仓库根目录执行：

```bash
# 1. 打包（每次改完代码后执行一次）
mvn -pl a2a-t-sample -am -DskipTests package

# 2. 查看有哪些 sample
java @a2a-t-sample/target/sample.args net.openan.a2at.sample.SampleRunner

# 3. 一条命令跑任意 sample（自动：起服务端 → 等就绪 → 跑客户端 → 回收服务端）
java @a2a-t-sample/target/sample.args net.openan.a2at.sample.SampleRunner service-recovery

#    可选：让客户端每轮只收 N 个 notification 就提前收尾（默认每轮收满 5 个、任务自动完结）：
A2AT_SAMPLE_MAX_ARTIFACTS=1 java @a2a-t-sample/target/sample.args net.openan.a2at.sample.SampleRunner service-recovery
#    PowerShell 写法：$env:A2AT_SAMPLE_MAX_ARTIFACTS="1"; java @... SampleRunner service-recovery
```

启动器按约定自动发现 sample：`src/main/resources/sample/<name>/` 下同时存在 `client/` 和
`server/` 两个子目录即视为一个 sample，主类按
`net.openan.a2at.sample.<name 转下划线>.client.ClientSampleMain` / `...server.ServerSampleMain`
解析。**新增 sample 无需任何注册**——放好包和资源目录即可被列出和运行。

未填写 LLM API key 时，sample 自动降级为确定性 mock LLM 模式，无需外部依赖即可完整跑通（适合 CI）。

## 当前示例

| sample | 说明 | 验证的 SDK API |
|---|---|---|
| `service-recovery` | 业务抢通事件订阅（Notification-T） | `generateNotificationPromptFromText`、`generateNotificationPromptFromDataWithSchema`、`validateAndFillingNotificationData` |
| `subscribe-incident` | Incident 事件订阅（Notification-T） | `generateTaskPrompt`（场景识别链路） |

- service-recovery 客户端在一个进程内跑两轮订阅：轮① `generateNotificationPromptFromText`
  （自然语言输入），轮② `generateNotificationPromptFromDataWithSchema`（结构化输入 + 数据
  schema），两轮各自生成的 prompt 都经服务端 `validateAndFillingNotificationData` 校验并建立
  真实订阅。每个订阅任务上报 5 次 notification（每 5 秒一次）后自动 COMPLETED，流自然结束。
  客户端在流程内输出 16 项 PASS/FAIL 检查，全部通过时进程退出码为 0。
- API key 填写位置：
  - service-recovery：`a2a-t-sample/src/main/resources/sample/service-recovery/{client,server}/` 下的 env
  - subscribe-incident：仓库根目录的 `client.env` / `server.env`
- service-recovery 的 env 使用 `local_file` 提示词资源模式（classpath 加载器目前拒绝含 `/` 的模板 URI），
  其相对根路径按 env 文件自身位置解析——从仓库根目录以 argfile/启动器方式运行即可，勿把 env 拷到别处单独使用。

## 单独运行某个进程（调试用）

统一启动器本身已覆盖绝大多数场景。如需长时间单独挂起某一个进程观察行为，用 exec:java
（见下节）在一个终端里跑 server、另一个终端跑 client 即可。

## 开发期免打包运行（exec:java）

```bash
mvn -pl a2a-t-sample exec:java \
  -Dexec.mainClass=net.openan.a2at.sample.service_recovery.server.ServerSampleMain

mvn -pl a2a-t-sample exec:java \
  -Dexec.mainClass=net.openan.a2at.sample.service_recovery.client.ClientSampleMain
```

## 新增 sample 的固定套路

1. 新建包 `net.openan.a2at.sample.<your_sample>`，包含 `client/ClientSampleMain` 与 `server/ServerSampleMain`
   （照抄任一现有 sample 的骨架）；
2. 新建资源目录 `src/main/resources/sample/<your-sample>/{client,server}/`，放入 `client.env`、`server.env`
   和 `scenario.json`；
3. 完成。pom 与 CI workflow 均无需改动——统一启动器按约定自动发现并可运行，CI 自动纳入。
   README 表格加一行即可（可选）。

## 已知限制

- SDK 的 classpath 提示词加载器目前会拒绝含 `/` 的模板 URI（如
  `Notification-T/network-layer/service-recovery/v1`），六个 `*FromText` / `*FromDataWithSchema`
  生成入口在 classpath 模式下不可用，故 sample 均以 `local_file` 模式运行。
- 各 sample 的服务端均先尝试向注册中心注册 AgentCard；注册中心不可用时自动跳过并切换为客户端直连
  模式（无需部署注册中心即可运行）。
