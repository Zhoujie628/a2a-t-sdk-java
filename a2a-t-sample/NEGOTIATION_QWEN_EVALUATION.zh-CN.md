# Negotiation-T Qwen3-32B 批量验证

此入口验证传输专线投诉诊断场景的 3 个自然语言生成接口：Propose、Accept、Reject。完整用例集为 100 条；另提供 20 条精简集，便于日常回归。用例由人工提供自然语言输入、补齐后的标准 Prompt 和期望业务字段；每条用例随后使用补齐 Prompt 调用对应的校验/提参接口。

生成接口与校验接口在用例中相互独立：生成 Prompt 用于人工判断生成质量，校验接口的输入是 `cases.json` 中预置的 `completedPrompt`，不能把生成 Prompt 直接送入校验接口。自动通过率只表示生成调用成功，且补齐 Prompt 能被校验并提取为期望字段，不能直接等同于生成语义准确率。应人工复核生成 Prompt，并复核全部校验失败用例。

## 用例集

资源文件是 [cases.json](src/main/resources/sample/private-line-complaint-negotiation/evaluation/cases.json)，包含：

- Propose 34 条；
- Accept 33 条；
- Reject 33 条。

覆盖完整表述、短句、同义改写、口语化表达、中英文混合、噪声信息、字段顺序变化、换行和业务上下文等类型。每条用例的 `completedPrompt` 是人工构造的补齐报文模板，`{{id}}`、`{{round}}`、`{{maxRounds}}` 会在运行时替换为本次协商上下文；`expected` 是人工 golden data。修改业务 Schema、报文格式或场景语义时，需要同步复核这两个字段。

精简集选择器为 `smoke`，共 20 条：Propose 7 条、Accept 7 条、Reject 6 条，覆盖完整、短句、上下文、同义改写、中英文混合、噪声、业务化和口语化表达。它从 `cases.json` 读取，不维护第二份用例数据；完整集选择器为 `full`（默认）。也可直接传逗号分隔的编号执行任意组合，例如 `P01,A28,R21`。

## 在目标网络执行

当前本机网络不能访问 Qwen 服务。进入东莞绿区 70 网段后，直接使用仓库提供的 [qwen.env](src/main/resources/sample/private-line-complaint-negotiation/qwen.env)。其中 `A2AT_LLM_DISABLE_SYSTEM_PROXY=true` 会绕过公司黄区的系统代理；该开关仅作用于此 LLM Client，不影响其他网络请求。不要修改默认的 Mock `client.env` 和 `server.env`。

先打包：

```powershell
mvn -pl a2a-t-sample -am -DskipTests package
```

再运行。参数依次为 env 文件、汇总报告输出路径、可选的过程日志输出路径、可选的用例选择器；不传第三个参数时，会在报告同目录生成同名的 `-process.jsonl` 文件。选择器省略或传 `full` 时运行 100 条，传 `smoke` 时运行 20 条，传逗号分隔的用例编号时运行指定用例：

```powershell
java @a2a-t-sample/target/negotiation-qwen-evaluation.javaargs.txt `
  a2a-t-sample/src/main/resources/sample/private-line-complaint-negotiation/qwen.env `
  a2a-t-sample/target/negotiation-qwen-report.json `
 a2a-t-sample/target/negotiation-qwen-process.jsonl
```

日常运行精简集：

```powershell
java @a2a-t-sample/target/negotiation-qwen-evaluation.javaargs.txt `
  a2a-t-sample/src/main/resources/sample/private-line-complaint-negotiation/qwen.env `
  a2a-t-sample/target/negotiation-qwen-smoke-report.json `
  a2a-t-sample/target/negotiation-qwen-smoke-process.jsonl `
  smoke
```

## SDK 问题最小复现

完整执行前可先运行以下 6 条用例，确认三类生成接口和三类补齐报文校验接口均能调用。用例内容、补齐 Prompt 和 golden data 均以 `cases.json` 为唯一来源。

```powershell
java @a2a-t-sample/target/negotiation-qwen-evaluation.javaargs.txt `
  a2a-t-sample/src/main/resources/sample/private-line-complaint-negotiation/qwen.env `
  a2a-t-sample/target/negotiation-qwen-repro-report.json `
  a2a-t-sample/target/negotiation-qwen-repro-process.jsonl `
  P01,P14,P16,A28,R01,R21
```

每次运行会在报告中写入 `git_revision`、`case_set` 和 `case_ids`，便于把结果与 SDK 代码版本和用例集合对应起来。过程日志是严格 JSONL：每行都是一条可独立解析的 JSON 事件。失败事件会额外记录 SDK `code` 和 `slot_errors`；其中 `negotiation_semantic_rejected` 能直接区分语义校验拒绝，`slot_errors` 可定位字段级规则问题。

汇总报告会分别输出 `generation_succeeded`、`validation_succeeded`、通过数和 `automatic_consistency_rate`，并逐条保存 `input`、`generated_prompt`、`completed_prompt`、`expected`、`actual`、阶段状态、耗时及结构化错误信息。即使校验抛出异常，报告仍保留生成 Prompt 和补齐 Prompt，并明确记录 `actual: null`。过程日志采用 JSONL，每一行对应一次 SDK 调用；其中包含 `run_id`、用例编号、阶段（`generate` 或 `validate_and_fill`）、请求入参、模型/SDK 返回值、耗时，以及异常类、异常消息、SDK 错误码、字段错误、cause 链和截断后的调用栈。

定位时先按 `run_id` 和 `case_id` 将同一用例的两行关联：`generate` 已失败时，重点检查 Sample 传入的自然语言、上下文和模板 URI；生成成功后，人工比较 `generated_prompt` 与场景要求；`validate_and_fill` 失败时，核对 `completed_prompt`、业务 Schema 和 SDK 校验异常。过程日志不记录 API Key。

执行过程会产生约 200 次模型调用（每个用例一次生成、一次校验/提参）；以 Qwen 响应时延为准设置 `A2AT_LLM_TIMEOUT_SECONDS`。

若目标网络的公司代理干扰访问，按 `C:\cnafan\git\design\Qwen3-32B模型调用指导.md` 的说明处理网络代理。Java SDK 当前 OpenAI-compatible Client 未额外加入 Qwen 专属逻辑；先保留实际失败报文，再判断是否需要在 SDK 层适配 `enable_thinking=false`。
