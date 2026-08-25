# Authorization-T Demo 运行与验收指南

本目录是 Authorization-T 演示（`net.openan.a2at.sample.authz_policy.AuthzSampleMain`）的资源根。
本文说明如何执行 demo、如何分析正确率报告，以及安全与判定约束。

## 1. 环境准备

### 1.1 构建

在仓库根目录执行（首次或代码/资源变更后）：

```
mvn -pl a2a-t-sample -am package -DskipTests -q
```

产物：`a2a-t-sample/target/authz.javaargs.txt`（classpath + main 类启动参数，由 pom 生成）。

### 1.2 准备 env 文件

demo 启动需要一个包含 LLM 网关凭据的 env 文件（参照本目录 `authz.env` 模板）：

```
A2AT_LANGUAGE=zh-CN
A2AT_PROMPT_SOURCE_TYPE=classpath
A2AT_LLM_PROVIDER=openai
A2AT_LLM_MODEL=<模型名>
A2AT_LLM_BASE_URL=<网关地址>
A2AT_LLM_API_KEY=<凭据>          # 绝不能提交、打印或写入任何产物
```

模板另含两个可忽略键（`A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR` 留空、
`A2AT_NEGOTIATION_STATE_STORE_TYPE` 默认 in_memory）；启动仅校验 provider/model/api_key 三键。

**安全约束**：
- env 文件命中 `.gitignore` 的 `*.env` 规则，永不提交；真实凭据只放在本地 env 文件中
- 本目录的 `authz.env` 是空 key 模板（仓库白名单放行），真实凭据版本写到仓库外或 gitignored 路径
- 凭据从安全来源（本地配置/密钥管理）获取，不得出现在命令行历史、日志、报告或提交内容中

### 1.3 运行

以下命令均在仓库根目录执行，`<env文件路径>` 替换为上一步准备的 env 文件（单行命令，任何 shell 通用）：

```
# 冒烟（默认题集，本目录 scenarios.json，12 例：a/b1-b5/c1-c4/c6 各组代表）
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 @a2a-t-sample/target/authz.javaargs.txt <env文件路径>

# 全量（原题集 100 例，评分基线用）
java -Dauthz.scenarios=sample/authz-policy/scenarios-100.json -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 @a2a-t-sample/target/authz.javaargs.txt <env文件路径>

# 留出集（holdout 100 例，防过拟合验证用）
java -Dauthz.scenarios=sample/authz-policy/scenarios-holdout-100.json -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 @a2a-t-sample/target/authz.javaargs.txt <env文件路径>
```

env 路径也可省略：省略时按"当前目录 authz.env → classpath 模板"顺序回退（模板无凭据，仅占位）。
退出码：0 = 全部匹配；非零 = 存在失败（注意：冒烟 11/12 属验收通过但退出码仍为非零，需按报告判读）。
控制台逐例输出 `[Client]/[Server]/[判定]` 三段式报告。

## 2. 报告分析

### 2.1 报告位置与结构

JSON 报告写入 `eval-results/authz-demo/authz-report-<yyyyMMdd-HHmmss>.json`
（可用 `-Dauthz.outdir=<目录>` 覆盖输出目录）。核心字段：

```
summary:    { total, match, mismatch }            # 总分
scenarios[]:
  label                    # 用例标识（组前缀 a/b1-b5/c1-c6）
  match                    # 本例是否通过（布尔）
  assertions:
    client_prompt          # 客户端 promptText 比对：true/false=DRIFT（漂移，不计分）/null=客户端失败路径
    server_outcome         # 服务端 outcome 断言（null=未到服务端）
    server_params          # 服务端 params 子集断言
  actual_outcome           # 实际结果码（success / slot_validation_error / validation_semantic_rejected / ...）
  actual_slot_errors[]     # 服务端实际错误明细（slot_name + code + message）
  actual_params            # 服务端实际提取参数
  prompt_text              # 客户端渲染产物（DRIFT 判读依据）
  warnings[]               # 结构性告警（如 empty_policy_list_section：增删改操作下策略列表章节为空），不计分
  error                    # 异常信息 {code, message}（失败归因用，如 sdk_internal_error）
```

**断言语义（重要）**：`match` 仅由客户端 outcome+错误码、服务端 outcome+错误码、params 子集匹配决定；
`client_prompt` 只记录漂移（DRIFT），不影响 match——措辞级漂移由人工判读 `prompt_text` 归类。

### 2.2 分析脚本核心

以下 python 代码读最新一份报告，输出总分、分组正确率、DRIFT/warnings 概览与失败清单。
保存为文件执行（`python -X utf8 <文件名>.py`）或粘贴到任意 python 环境：

```python
import json, glob, os, sys

sys.stdout.reconfigure(encoding="utf-8")
files = glob.glob("eval-results/authz-demo/authz-report-*.json")
if not files:
    raise SystemExit("no report found under eval-results/authz-demo/")
path = max(files, key=os.path.getmtime)
r = json.load(open(path, encoding="utf-8"))
cs = r["scenarios"]

groups = {}
for c in cs:
    g = groups.setdefault(c["label"].split("-")[0], {"n": 0, "ok": 0})
    g["n"] += 1
    g["ok"] += c["match"]

print("报告: %s" % path)
print("总分: %s/%s" % (r["summary"]["match"], r["summary"]["total"]))
print("分组: " + "  ".join("%s=%s/%s" % (k, v["ok"], v["n"]) for k, v in sorted(groups.items())))

drift = [c["label"] for c in cs if (c.get("assertions") or {}).get("client_prompt") is False]
warn = [c["label"] for c in cs if c.get("warnings")]
print("DRIFT(不计分): %d 例 %s" % (len(drift), drift))
print("warnings(不计分): %d 例 %s" % (len(warn), warn))

for c in cs:
    if not c["match"]:
        a = c.get("assertions") or {}
        why = (
            "client outcome/slot_errors" if a.get("client_prompt") is None
            else "server outcome" if a.get("server_outcome") is False
            else "server params" if a.get("server_params") is False
            else "slot code"
        )
        print("  FAIL %s [%s] actual=%s errors=%s" % (
            c["label"], why, c.get("actual_outcome"),
            [(e["slot_name"], e["code"]) for e in c.get("actual_slot_errors") or []]))
```

失败断言定位口诀：`client_prompt is null` → 客户端阶段失败（对照期望 outcome）；
`server_outcome is False` → 服务端判定与期望不符（对照 actual_slot_errors 的 slot+code）；
`server_params is False` → 提参结果与期望 params 子集不匹配。

## 3. 验收标准

| 模式 | 题集 | 通过线 |
|---|---|---|
| 冒烟 | `scenarios.json`（12 例） | ≥ 11/12，且无 `sdk_internal_error` 类异常 |
| 全量 | `scenarios-100.json`（100 例） | 记录基线分数，失败例逐例归因（LLM 概率性 / 判据缺口 / 题集期望错误） |
| 留出集 | `scenarios-holdout-100.json`（100 例） | 与全量分差 ≤5 视为无过拟合；分差扩大时优先排查 prompt/约束对原题集的过拟合 |

## 4. 迭代约定

- 改动 prompt（slot_extraction / content_validation）、slot.json、param-schema 或题集后，先跑冒烟，再同时跑全量与留出集
- 题集期望修订原则：期望必须与"服务端可见资源（param-schema + template + content_validation）"中的判据一致；
  不得为通过用例而在资源中发明仅服务于探针的判据
- 每轮全量结果保留 JSON 报告于 eval-results/（gitignored），跨轮对比用 2.2 的分析脚本核心
