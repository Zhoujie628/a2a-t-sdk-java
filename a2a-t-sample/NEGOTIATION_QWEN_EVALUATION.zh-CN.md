# Negotiation-T Qwen3-32B 批量验证

此入口验证传输专线投诉诊断场景的 3 个自然语言生成接口：Propose、Accept、Reject。用例集固定为 100 条，由人工标注期望业务字段；每条用例还会调用对应的校验/提参接口，检查生成 Prompt 能否被 SDK 解析为期望字段并保留协商上下文。

自动结果衡量的是“生成 Prompt → 校验提参”的一致性，不能直接等同于 LLM 语义准确率。报告会保存原始输入、生成 Prompt、提取结果和错误信息。应人工复核全部失败用例，并在每个阶段从通过用例中至少抽查 10 条。

## 用例集

资源文件是 [cases.json](src/main/resources/sample/private-line-complaint-negotiation/evaluation/cases.json)，包含：

- Propose 34 条；
- Accept 33 条；
- Reject 33 条。

覆盖完整表述、短句、同义改写、口语化表达、中英文混合、噪声信息、字段顺序变化、换行和业务上下文等类型。每条用例的 `expected` 是人工 golden data；修改业务 Schema 或场景语义时，需要同步复核该文件。

## 在目标网络执行

当前本机网络不能访问 Qwen 服务。进入东莞绿区 70 网段后，直接使用仓库提供的 [qwen.env](src/main/resources/sample/private-line-complaint-negotiation/qwen.env)。其中 `A2AT_LLM_DISABLE_SYSTEM_PROXY=true` 会绕过公司黄区的系统代理；该开关仅作用于此 LLM Client，不影响其他网络请求。不要修改默认的 Mock `client.env` 和 `server.env`。

先打包：

```powershell
mvn -pl a2a-t-sample -am -DskipTests package
```

再运行。参数依次为 env 文件、汇总报告输出路径、可选的过程日志输出路径；不传第三个参数时，会在报告同目录生成同名的 `-process.jsonl` 文件：

```powershell
java @a2a-t-sample/target/negotiation-qwen-evaluation.javaargs.txt `
  a2a-t-sample/src/main/resources/sample/private-line-complaint-negotiation/qwen.env `
  a2a-t-sample/target/negotiation-qwen-report.json `
  a2a-t-sample/target/negotiation-qwen-process.jsonl
```

汇总报告会输出总数、通过数和 `automatic_consistency_rate`，并逐条保存 `input`、`prompt`、`expected`、`actual`、耗时及错误信息。过程日志采用 JSONL，每一行对应一次 SDK 调用；其中包含 `run_id`、用例编号、阶段（`generate` 或 `validate_and_fill`）、请求入参、模型/SDK 返回值、耗时，以及异常类、异常消息、cause 链和截断后的调用栈。

定位时先按 `run_id` 和 `case_id` 将同一用例的两行关联：`generate` 已失败时，重点检查 Sample 传入的自然语言、上下文和模板 URI，以及 SDK 的生成接口异常；生成成功但 `validate_and_fill` 失败时，直接核对生成 Prompt、业务 Schema 和 SDK 校验异常。这样无需重跑模型即可判断问题发生在流程的哪个边界。过程日志不记录 API Key。

执行过程会产生约 200 次模型调用（每个用例一次生成、一次校验/提参）；以 Qwen 响应时延为准设置 `A2AT_LLM_TIMEOUT_SECONDS`。

若目标网络的公司代理干扰访问，按 `C:\cnafan\git\design\Qwen3-32B模型调用指导.md` 的说明处理网络代理。Java SDK 当前 OpenAI-compatible Client 未额外加入 Qwen 专属逻辑；先保留实际失败报文，再判断是否需要在 SDK 层适配 `enable_thinking=false`。
