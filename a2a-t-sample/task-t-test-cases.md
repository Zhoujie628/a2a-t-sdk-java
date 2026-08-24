# Task-T 准确率验证测试用例集

本文档维护 `net.openan.a2at.sample.task_t.TaskTDemoMain` 的闭环验证用例：客户端 facade 生成结构化
`Task-T` prompt → 服务端 facade 校验并重新提取参数 → 与期望值（ground truth）比对，统计字段准确率与
样本通过率。

- 模板：`Task-T/network-layer/private-line-complaint/v1`（`StandardTemplates.PRIVATE_LINE_COMPLAINT`，传输专线业务投诉诊断）
- 数据源：`src/main/resources/sample/task_t/private-line-complaint-samples.json`
  （用例输入均在此 JSON 中维护，改这里即可扩展/替换场景；样例类 `TaskTPrivateLineComplaintSamples.java`
  仅负责从 classpath 加载并按 `text`/`data` 分组）
- 命中规则（分字段两档）：结构化字段（`accessPort`/`bizScenario`/`faultTime`/`eventSerialNo`）去除空白并统一小写后
  **完全相等**即为命中，被截断的前缀（如仅提取 `P781`）视为未命中；详情字段（`faultDetail`）归一化后**相同或互相包含**
  即命中（自由文本天然比期望关键词长）；提取值缺失视为未命中
- 执行方式：`TaskTDemoMain [env-file] [case]` —— `case` 可选 `text`/`data`/`rejection`/`all`，不传则执行整组用例。

## 客户端 / 服务端字段对照

两端 key 语义近似但**字段名不一致**，用于验证 a2a-t SDK 跨 key 适配能力（客户端 key → 模板渲染 →
服务端 key 提取）。

| 客户端 key（入参 data / 语义 schema） | 服务端 key（校验 schema / 提取参数 / 期望值） | 含义 |
|---|---|---|
| `portName` | `accessPort` | 接入端口名称 |
| `complaintScenario` | `bizScenario` | 投诉分类（专线中断 / 专线质差） |
| `faultStartTime` | `faultTime` | 问题发生时间 |
| `ticketNo` | `eventSerialNo` | OSS 侧事件流水号 |
| `faultDetailText` | `faultDetail` | 投诉/故障详情 |

## 用例一：generateTaskPromptFromText（中文口语化自然语言，验证 NL 解析能力）

| # | 样例名 | 端口 | 分类 | 时间 | 流水号 | 详情期望关键词 |
|---|---|---|---|---|---|---|
| TC-01 | text-private-line-quality | P781-珠江新城-PTN7900-23-TPA1EG24-17 | 专线质差 | 2026-05-11 | event-id-20260511-09013 | 320ms |
| TC-02 | text-logical-port-interruption | P781-前海-PTN7900-5-TPA1EG24-09(cvlan=100) | 专线中断 | 2026-05-16 | fault-id-1-017-20260516-11234 | 完全中断 |
| TC-03 | text-port-quality-jitter | P781-惠州江北-PTN7900-7-TPA1EG24-21 | 专线质差 | 2026-05-22 | event-id-20260522-00812 | 丢包 |
| TC-04 | text-port-interruption-route | P781-南沙-PTN7900-9-TPA1EG24-31 | 专线中断 | 2026-06-03 | event-id-20260603-02341 | 彻底不通 |
| TC-05 | text-port-quality-latency | P781-天河体育西-PTN7900-4-TPA1EG24-11 | 专线质差 | 2026-06-08 | event-id-20260608-01102 | 时延 |
| TC-06 | text-logical-port-interruption-vlan | P781-宝安中心-PTN7900-6-TPA1EG24-27(cvlan=200) | 专线中断 | 2026-06-12 | fault-id-9-023-20260612-04578 | 连不上 |
| TC-07 | text-optional-slots-missing | P781-番禺广场-PTN7900-3-TPA1EG24-19 | 专线质差 | — | — | 卡顿 |

## 用例二：generateTaskPromptFromDataWithSchema（结构化英文业务字段 + 语义 schema）

| # | 样例名 | 端口 | 分类 | 时间（ISO-8601） | 流水号 | 详情期望关键词 |
|---|---|---|---|---|---|---|
| TC-08 | data-port-quality | P781-福田中心-PTN7900-2-TPA1EG24-03 | 专线质差 | 2026-05-11T08:21:46Z | event-id-20260511-09013 | 320ms |
| TC-09 | data-logical-port-interruption | P781-前海-PTN7900-5-TPA1EG24-09(cvlan=100) | 专线中断 | 2026-05-16T12:10:00Z | fault-id-1-017-20260516-11234 | 完全中断 |
| TC-10 | data-port-jitter | P781-惠州江北-PTN7900-7-TPA1EG24-21 | 专线质差 | 2026-05-22T14:05:00Z | event-id-20260522-00812 | 时延抖动 |
| TC-11 | data-logical-port-interruption-route | P781-云浮-PTN7900-1-TPA1EG24-08(cvlan=300) | 专线中断 | 2026-06-03T09:40:00Z | event-id-20260603-02341 | 完全中断 |
| TC-12 | data-port-quality-loss | P781-珠海吉大-PTN7900-6-TPA1EG24-13 | 专线质差 | 2026-06-08T15:20:00Z | event-id-20260608-01102 | 丢包 |
| TC-13 | data-port-quality-core-slow | P781-罗湖东门-PTN7900-8-TPA1EG24-15 | 专线质差 | 2026-06-18T10:05:00Z | event-id-20260618-03342 | 时延 |
| TC-14 | data-optional-slots-missing | P781-番禺市桥-PTN7900-7-TPA1EG24-18 | 专线中断 | — | — | 完全中断 |

> 说明：
> 1. 用例一（text）的时间期望取日期前缀（如 `2026-05-11`）——口语文本不含 ISO 字面量，日期是稳定事实；
>   用例二（data）的时间期望取完整 ISO-8601 时间戳。
> 2. 期望值均按**服务端 key** 记分（`accessPort`/`bizScenario`/`faultTime`/`eventSerialNo`/`faultDetail`），
>   与 `validateTaskPromptAndDataFilling` 实际输出一致。
> 3. 详情字段命中规则较宽松（等价或互相包含）；结构化字段需归一化后**完全相等**，被截断的前缀不命中。
> 4. TC-07/TC-14 为**可选槽位观测用例**：输入故意缺时间与流水号（二者均为可选槽位），期望校验**通过**且其余
>    字段命中——验证"可选槽位缺失不纳入拒绝集"这一校验口径；其期望值不含该两项，不进入字段准确率分母。

## 用例三：缺少关键槽位拒绝用例（期望 rejected）

正向样本评测的是提取准确率；本组用例验证语义校验的**兜底能力**——输入内容故意缺失**必填槽位**（接入端口、
投诉分类）或携带**枚举外取值**，服务端应抛出 `validation_semantic_rejected`。与用例一/二对应，本组同时覆盖
**文本变体**（`generateTaskPromptFromText`）与**数据变体**（`generateTaskPromptFromDataWithSchema`）。
被拦截样本数占样本总数的比例即**拦截率**（服务端语义拒绝 + 客户端生成阶段拦截均计为拦截）；意外通过时打印
实际提取参数以便排查。本组用例独立于正向样本计分，不进入字段准确率统计。

> 拒绝集口径：仅以**契约级硬项**为拒绝依据——违反任一 schema 的 `required`（服务端 `accessPort`/`bizScenario`，
> 客户端 `portName`/`complaintScenario`）或取值不在**契约枚举**（两端 `complainScenario`/`bizScenario` 的 `enum`）。
> 时间、流水号均为**可选槽位**，不在拒绝集内；若需观察可选槽位缺失的行为，应作为正向观察用例而非拒绝断言，
> 避免依赖未契约化的语义规则造成拦截率波动。
>
> 拦截结果按**双口径**报告：**服务端校验点拒绝率**（仅统计 `validation_semantic_rejected`，衡量服务端校验点本身的
> 拦截能力）与**总拦截率**（服务端拒绝 + 客户端生成阶段拦截，衡量整条链路的兜底能力）——客户端拦截发生在模板
> 槽位填充阶段，与服务端语义校验是两套判断，分开统计才能定位到具体层。

### 文本变体（generateTaskPromptFromText）

| # | 样例名 | 缺失槽位 | 保留内容 |
|---|---|---|---|
| TC-15 | text-missing-access-port | 接入端口 | 分类、时间、详情、流水号 |
| TC-16 | text-missing-scenario | 投诉分类 | 端口、时间、流水号 |
| TC-17 | text-minimal-no-key-fields | 全部关键槽位 | 仅一句"专线业务好像出问题了" |
| TC-18 | text-missing-scenario-and-serial | 投诉分类 + 流水号 | 端口、时间、详情 |

### 数据变体（generateTaskPromptFromDataWithSchema，字段为客户端 key）

| # | 样例名 | 缺失 / 非法内容 | 保留内容 |
|---|---|---|---|
| TC-19 | data-missing-port | 缺 `portName`（→ 服务端缺必填 `accessPort`） | 分类、时间、流水号、详情 |
| TC-20 | data-missing-scenario | 缺 `complaintScenario`（→ 服务端缺必填 `bizScenario`） | 端口、时间、流水号、详情 |
| TC-21 | data-invalid-scenario-value | `complaintScenario` 取值"专线掉线"（不在契约 `enum`） | 端口、时间、流水号、详情 |
| TC-22 | data-missing-port-and-scenario | 缺 `portName` + `complaintScenario` | 时间、流水号、详情 |