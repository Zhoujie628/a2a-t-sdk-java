# Negotiation-T Qwen3-32B 批量验证

此入口验证传输专线投诉诊断场景的 3 组接口：Propose、Accept、Reject 各自的自然语言生成接口和对应校验/提参接口。完整集为 100 条端到端流程；另提供 20 条精简集，便于日常回归。

每条流程依次执行：Propose 生成、Propose 校验、客户端补充信息、Accept 或 Reject 生成、对应校验。生成接口的实际 Prompt 直接作为配对校验接口的输入；客户端补充信息是无 SDK 校验的中间业务数据。两次生成和两次校验均成功返回时，端到端流程通过。

## 用例集

资源文件是 [cases.json](src/main/resources/sample/private-line-complaint-negotiation/evaluation/cases.json)，包含：

- Propose 34 条；
- Accept 33 条；
- Reject 33 条。

覆盖完整表述、短句、同义改写、口语化表达、中英文混合、噪声信息、字段顺序变化、换行和业务上下文等类型。加载器把每个原始编号组装成完整流程：P 编号重点覆盖 Propose 输入并确定性配对一个 Accept/Reject 输入；A/R 编号重点覆盖结论输入并确定性配对一个 Propose 输入。最终 100 条流程包含 50 条 Accept 和 50 条 Reject。`expected` 是对应阶段的人工 golden data；`completedPrompt` 作为历史标准报文保留在语料中，不再用于校验接口入参。

精简集选择器为 `smoke`，共 20 条完整流程，兼顾 Propose 输入及 Accept/Reject 两种结论，覆盖完整、短句、上下文、同义改写、中英文混合、噪声、业务化和口语化表达。它从 `cases.json` 组装，不维护第二份用例数据；完整集选择器为 `full`（默认）。也可直接传逗号分隔的编号执行任意组合，例如 `P01,A28,R21`。

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

只查看一条 Accept 端到端流程时，可运行 `A01`：

```powershell
java @a2a-t-sample/target/negotiation-qwen-evaluation.javaargs.txt `
  a2a-t-sample/src/main/resources/sample/private-line-complaint-negotiation/qwen.env `
  a2a-t-sample/target/negotiation-qwen-example-report.json `
  a2a-t-sample/target/negotiation-qwen-example-process.jsonl `
  A01
```

运行完成后，以下命令会单独打印这条流程的四个接口输入和输出：

```powershell
(Get-Content -Raw a2a-t-sample/target/negotiation-qwen-example-report.json |
  ConvertFrom-Json).cases[0].api_trace | ConvertTo-Json -Depth 20
```

## SDK 问题最小复现

完整执行前可先运行以下 6 条流程，确认三组生成与校验接口均能调用。自然语言输入和 golden data 均以 `cases.json` 为唯一来源。

```powershell
java @a2a-t-sample/target/negotiation-qwen-evaluation.javaargs.txt `
  a2a-t-sample/src/main/resources/sample/private-line-complaint-negotiation/qwen.env `
  a2a-t-sample/target/negotiation-qwen-repro-report.json `
  a2a-t-sample/target/negotiation-qwen-repro-process.jsonl `
  P01,P14,P16,A28,R01,R21
```

每次运行会在报告中写入 `git_revision`、`case_set` 和 `case_ids`，便于把结果与 SDK 代码版本和用例集合对应起来。过程日志是严格 JSONL：每行都是一条可独立解析的 JSON 事件。失败事件会额外记录 SDK `code` 和 `slot_errors`；其中 `negotiation_semantic_rejected` 能直接区分语义校验拒绝，`slot_errors` 可定位字段级规则问题。

汇总报告以 `passed` 和 `end_to_end_success_rate` 作为主验证结论，表示四个接口是否完整串通且没有抛出异常；`propose_succeeded`、`ending_succeeded` 分别统计两组配对接口是否成功。每条流程保存 Propose/Ending 的自然语言输入、生成 Prompt、期望值、实际提取值、客户端补充信息、四个调用阶段的状态、耗时及结构化错误。

`propose_expected_matched`、`ending_expected_matched`、`golden_matched` 和 `golden_exact_match_rate` 使用严格值比较，只作为提参差异的辅助诊断指标，不影响端到端通过率。自然语言可能出现语义等价但字面不同的结果，例如“物理或逻辑端口”和“物理端口或逻辑端口名称”；这类情况应在端到端指标中计为成功。`propose_context_matched` 和 `ending_context_matched` 单独记录上下文传递是否一致。

每条流程的 `api_trace` 按调用顺序保存一组可连续阅读的端到端记录。Accept 流程依次为：

1. `A2ATClient.generateNegotiationProposePromptFromText`：发起生成；
2. `A2ATServer.validateProposePromptAndDataFilling`：发起校验和提参；
3. `A2ATServer.generateNegotiationAcceptPromptFromText`：接受生成；
4. `A2ATClient.validateAcceptPromptAndDataFilling`：接受校验和提参。

Reject 流程的第 3、4 步替换为对应的 Reject 接口。每一步均包含 `step_label`、`api`、`caller`、`request`、`response`、`expected`、耗时和结果；发生异常时保存结构化错误。过程日志采用 JSONL，同一 `case_id` 的四行与报告中的 `api_trace` 内容一致，便于流式查看和故障检索。

定位时按 `run_id` 和 `case_id` 关联同一流程的四次调用。生成失败时检查自然语言、上下文和模板 URI；校验失败时直接核对前一阶段的 `response.prompt`、业务 Schema 和 SDK 异常。客户端补充信息会记录在 Ending 生成请求中，但不会调用校验 API。过程日志不记录 API Key。

完整集会产生约 400 次模型调用，精简集约 80 次模型调用（每条流程两次生成、两次校验/提参）；以 Qwen 响应时延为准设置 `A2AT_LLM_TIMEOUT_SECONDS`。

若目标网络的公司代理干扰访问，按 `C:\cnafan\git\design\Qwen3-32B模型调用指导.md` 的说明处理网络代理。Java SDK 当前 OpenAI-compatible Client 未额外加入 Qwen 专属逻辑；先保留实际失败报文，再判断是否需要在 SDK 层适配 `enable_thinking=false`。
