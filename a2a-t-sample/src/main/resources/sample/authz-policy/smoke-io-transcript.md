# Authorization-T Demo 冒烟测试原始输入输出记录

- 位置：`eval-results\authz-demo\authz-report-20260828-110442.json`；题集：`sample/authz-policy/scenarios.json`；LLM：蓝区统一网关（key 不落档）；参数：`-Dauthz.reasoning=true`；结果：**15/15 match**
- 分区：预期成功在前（index 0-7）、预期拒绝在中（index 8-12）、客户端拦截在末尾（index 13-14）
- 每例两段：客户端（输入→生成结果→理由）与服务端（schema 输入→校验提参结果→理由）；服务端段不重复渲染后 prompt

---

## c1-nl-add-01  —  ✅ match

### 客户端（生成）

**原始输入**（from_text）

> 帮我新增一个授权策略，业务场景是校园专网，处置类型是业务抢通，操作名称是隧道调优，有效期从2026-01-01到2030-12-31

**生成结果：成功（渲染后 prompt）**

```
## 授权策略的操作类型
新增授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
1. 业务场景是校园专网，处置类型是业务抢通，操作名称是隧道调优，有效期是2026-01-01~2030-12-31

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

**提参理由（LLM reasoning）**：输入中包含“新增一个授权策略”，对应封闭取值范围内的“新增授权策略”。动网操作的授权策略列表按新增形态组织为编号条目，包含业务场景、处置类型、操作名称、有效期四个字段，并以全角逗号分隔；日期“2026-01-01到2030-12-31”按约束归一为“2026-01-01~2030-12-31”。所有必填内容均已提供，无错误。

### 服务端（校验 + 提参）

**validateAndDataFilling 输入 schema（suite 默认 param-schema.json）**：

```json
{
  "type": "object",
  "properties": {
    "operationType": {
      "type": "string",
      "enum": [
        "新增授权策略",
        "修改授权策略",
        "删除授权策略",
        "查询授权策略"
      ],
      "description": "授权策略的操作类型"
    },
    "policyList": {
      "type": "array",
      "description": "动网操作的授权策略列表，按输入中的出现顺序排列；未提供时为空列表",
      "items": {
        "type": "object",
        "properties": {
          "policyId": {
            "type": "string",
            "description": "策略的唯一标识"
          },
          "scene": {
            "type": "string",
            "description": "动网操作支撑的业务场景"
          },
          "actionType": {
            "type": "string",
            "description": "动网操作的处置类型"
          },
          "operationName": {
            "type": "string",
            "description": "所授权的动网操作的具体名称"
          },
          "validityPeriod": {
            "type": "string",
            "description": "授权策略的生效时间区间"
          }
        }
      }
    }
  }
}
```

（输入 prompt 即上方客户端渲染结果，此处不重复。）

**校验与提参结果**：`success`

**提参结果**：

```json
{
  "operationType": "新增授权策略",
  "policyList": [
    {
      "policyId": null,
      "scene": "校园专网",
      "actionType": "业务抢通",
      "operationName": "隧道调优",
      "validityPeriod": "2026-01-01~2030-12-31"
    }
  ]
}
```

**校验理由（LLM reasoning）**：操作类型为已提供的枚举值“新增授权策略”。动网操作的授权策略列表存在1条非空条目，新增授权策略所需的业务场景、处置类型、操作名称、有效期均已提供，未提供policyId不构成缺失。有效期区间完整且起止日期顺序有效，未发现格式或语义冲突，因此整体校验通过，errors为空。

**键名泄漏扫描**：无泄漏

**判定**：match=True（client_prompt=True）

---

## c1-nl-add-01-varname  —  ✅ match
[【差分对】配对基线：c1-nl-add-01]

### 客户端（生成）

**原始输入**（from_text）

> 帮我新增一个授权策略，业务场景是校园专网，处置类型是业务抢通，操作名称是隧道调优，有效期从2026-01-01到2030-12-31

**生成结果：成功（渲染后 prompt）**

```
## 授权策略的操作类型
新增授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
1. 业务场景是校园专网，处置类型是业务抢通，操作名称是隧道调优，有效期是2026-01-01~2030-12-31

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

**提参理由（LLM reasoning）**：输入中“新增”是封闭取值范围中“新增授权策略”的合法简写，按规则13a映射。操作类型为新增，策略列表为必填，输入提供了业务场景、处置类型、操作名称、有效期四个字段，按约束组织为编号条目；有效期从“从2026-01-01到2030-12-31”归一为区间格式“2026-01-01~2030-12-31”。

### 服务端（校验 + 提参）

**validateAndDataFilling 输入 schema（本用例变异 schema）**：

```json
{
  "type": "object",
  "properties": {
    "operationType": {
      "type": "string",
      "enum": [
        "新增授权策略",
        "修改授权策略",
        "删除授权策略",
        "查询授权策略"
      ],
      "description": "授权策略的操作类型"
    },
    "policies": {
      "type": "array",
      "description": "动网操作的授权策略列表，按输入中的出现顺序排列；未提供时为空列表",
      "items": {
        "type": "object",
        "properties": {
          "pid": {
            "type": "string",
            "description": "策略的唯一标识"
          },
          "domain": {
            "type": "string",
            "description": "动网操作支撑的业务场景"
          },
          "handling": {
            "type": "string",
            "description": "动网操作的处置类型"
          },
          "opName": {
            "type": "string",
            "description": "所授权的动网操作的具体名称"
          },
          "effectivePeriod": {
            "type": "string",
            "description": "授权策略的生效时间区间"
          }
        }
      }
    }
  }
}
```

（输入 prompt 即上方客户端渲染结果，此处不重复。）

**校验与提参结果**：`success`

**提参结果**：

```json
{
  "operationType": "新增授权策略",
  "policies": [
    {
      "pid": null,
      "domain": "校园专网",
      "handling": "业务抢通",
      "opName": "隧道调优",
      "effectivePeriod": "2026-01-01~2030-12-31"
    }
  ]
}
```

**校验理由（LLM reasoning）**：操作类型为“新增授权策略”，属于合法取值。新增场景下必填字段为业务场景、处置类型、操作名称、有效期；策略列表第1条已完整提供这四项，且有效期起止日期完整、无明显格式或区间倒置问题。策略标识未见提供，但新增场景未强制要求，按schema输出为null。因此校验通过。

**键名泄漏扫描**：无泄漏

**判定**：match=True（client_prompt=True）

---

## c2-nl-multi-01  —  ✅ match

### 客户端（生成）

**原始输入**（from_text）

> 新增两条授权策略：第一条，业务场景是校园专网，处置类型是业务抢通，操作名称是隧道调优，有效期从2026-01-01到2030-12-31；第二条，业务场景是医疗专线，处置类型是业务恢复，操作名称是频段调整，有效期从2026-06-01到2030-06-18

**生成结果：成功（渲染后 prompt）**

```
## 授权策略的操作类型
新增授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
1. 业务场景是校园专网，处置类型是业务抢通，操作名称是隧道调优，有效期是2026-01-01~2030-12-31
2. 业务场景是医疗专线，处置类型是业务恢复，操作名称是频段调整，有效期是2026-06-01~2030-06-18

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

**提参理由（LLM reasoning）**：输入中“新增两条授权策略”中的“新增”为合法取值范围中“新增授权策略”的简写，按规则13a映射为“新增授权策略”。“动网操作的授权策略列表”在新增操作下为必填，输入明确给出两条策略，分别提取业务场景、处置类型、操作名称、有效期，并按规则18将自然语言日期“从2026-01-01到2030-12-31”归一为“2026-01-01~2030-12-31”，第二条同理归一为“2026-06-01~2030-06-18”；按条目编号和全角逗号分隔组织为列表文本。未发现缺失或非法值，故无错误。

### 服务端（校验 + 提参）

**validateAndDataFilling 输入 schema（suite 默认 param-schema.json）**：

```json
{
  "type": "object",
  "properties": {
    "operationType": {
      "type": "string",
      "enum": [
        "新增授权策略",
        "修改授权策略",
        "删除授权策略",
        "查询授权策略"
      ],
      "description": "授权策略的操作类型"
    },
    "policyList": {
      "type": "array",
      "description": "动网操作的授权策略列表，按输入中的出现顺序排列；未提供时为空列表",
      "items": {
        "type": "object",
        "properties": {
          "policyId": {
            "type": "string",
            "description": "策略的唯一标识"
          },
          "scene": {
            "type": "string",
            "description": "动网操作支撑的业务场景"
          },
          "actionType": {
            "type": "string",
            "description": "动网操作的处置类型"
          },
          "operationName": {
            "type": "string",
            "description": "所授权的动网操作的具体名称"
          },
          "validityPeriod": {
            "type": "string",
            "description": "授权策略的生效时间区间"
          }
        }
      }
    }
  }
}
```

（输入 prompt 即上方客户端渲染结果，此处不重复。）

**校验与提参结果**：`success`

**提参结果**：

```json
{
  "operationType": "新增授权策略",
  "policyList": [
    {
      "policyId": null,
      "scene": "校园专网",
      "actionType": "业务抢通",
      "operationName": "隧道调优",
      "validityPeriod": "2026-01-01~2030-12-31"
    },
    {
      "policyId": null,
      "scene": "医疗专线",
      "actionType": "业务恢复",
      "operationName": "频段调整",
      "validityPeriod": "2026-06-01~2030-06-18"
    }
  ]
}
```

**校验理由（LLM reasoning）**：操作类型为“新增授权策略”，符合 schema 枚举；授权策略列表已提供两条，新增场景下必填的业务场景、处置类型、操作名称、有效期均已提供；有效期起止日期有效且起止顺序未倒置。策略标识在新增场景中未提供，不构成缺失。

**键名泄漏扫描**：无泄漏

**判定**：match=True（client_prompt=True）

---

## c3-nl-mod-06  —  ✅ match

### 客户端（生成）

**原始输入**（from_text）

> 把7d8c7b00-3c8c-4f8e-9b1e-9b17b6a3e5c3和a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d的有效期都改成永久生效

**生成结果：成功（渲染后 prompt）**

```
## 授权策略的操作类型
修改授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
1. 策略标识是7d8c7b00-3c8c-4f8e-9b1e-9b17b6a3e5c3，有效期是永久生效
2. 策略标识是a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d，有效期是永久生效

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

**提参理由（LLM reasoning）**：输入中的“改成”命中修改授权策略的同义对清单，因此“授权策略的操作类型”提取为“修改授权策略”。输入中明确给出两个策略标识，并要求将二者的有效期改为永久生效，属于正向修改约束，因此按修改形态分别形成编号条目：“1. 策略标识是...，有效期是永久生效”和“2. 策略标识是...，有效期是永久生效”，以换行分隔。

### 服务端（校验 + 提参）

**validateAndDataFilling 输入 schema（suite 默认 param-schema.json）**：

```json
{
  "type": "object",
  "properties": {
    "operationType": {
      "type": "string",
      "enum": [
        "新增授权策略",
        "修改授权策略",
        "删除授权策略",
        "查询授权策略"
      ],
      "description": "授权策略的操作类型"
    },
    "policyList": {
      "type": "array",
      "description": "动网操作的授权策略列表，按输入中的出现顺序排列；未提供时为空列表",
      "items": {
        "type": "object",
        "properties": {
          "policyId": {
            "type": "string",
            "description": "策略的唯一标识"
          },
          "scene": {
            "type": "string",
            "description": "动网操作支撑的业务场景"
          },
          "actionType": {
            "type": "string",
            "description": "动网操作的处置类型"
          },
          "operationName": {
            "type": "string",
            "description": "所授权的动网操作的具体名称"
          },
          "validityPeriod": {
            "type": "string",
            "description": "授权策略的生效时间区间"
          }
        }
      }
    }
  }
}
```

（输入 prompt 即上方客户端渲染结果，此处不重复。）

**校验与提参结果**：`success`

**提参结果**：

```json
{
  "operationType": "修改授权策略",
  "policyList": [
    {
      "policyId": "7d8c7b00-3c8c-4f8e-9b1e-9b17b6a3e5c3",
      "scene": null,
      "actionType": null,
      "operationName": null,
      "validityPeriod": "永久生效"
    },
    {
      "policyId": "a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
      "scene": null,
      "actionType": null,
      "operationName": null,
      "validityPeriod": "永久生效"
    }
  ]
}
```

**校验理由（LLM reasoning）**：（未采集）

**键名泄漏扫描**：无泄漏

**判定**：match=True（client_prompt=True）

---

## c3-nl-mod-06-varfields  —  ✅ match
[【差分对】配对基线：c3-nl-mod-06]

### 客户端（生成）

**原始输入**（from_text）

> 把7d8c7b00-3c8c-4f8e-9b1e-9b17b6a3e5c3和a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d的有效期都改成永久生效

**生成结果：成功（渲染后 prompt）**

```
## 授权策略的操作类型
修改授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
1. 策略标识是7d8c7b00-3c8c-4f8e-9b1e-9b17b6a3e5c3，有效期是永久生效
2. 策略标识是a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d，有效期是永久生效

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

**提参理由（LLM reasoning）**：输入包含“改成”，属于操作类型同义对清单中的“修改授权策略”映射；两个 UUID 均为策略标识，且“都改成永久生效”表示对两者修改有效期，因此按修改授权策略的条目格式生成两条编号条目。无缺失或非法值。

### 服务端（校验 + 提参）

**validateAndDataFilling 输入 schema（本用例变异 schema）**：

```json
{
  "type": "object",
  "properties": {
    "operationType": {
      "type": "string",
      "enum": [
        "新增授权策略",
        "修改授权策略",
        "删除授权策略",
        "查询授权策略"
      ],
      "description": "授权策略的操作类型"
    },
    "policyList": {
      "type": "array",
      "description": "动网操作的授权策略列表，按输入中的出现顺序排列；未提供时为空列表",
      "items": {
        "type": "object",
        "properties": {
          "policyId": {
            "type": "string",
            "description": "策略的唯一标识"
          },
          "newValidityPeriod": {
            "type": "string",
            "description": "修改后的生效时间区间，YYYY-MM-DD~YYYY-MM-DD或永久生效"
          }
        }
      }
    }
  }
}
```

（输入 prompt 即上方客户端渲染结果，此处不重复。）

**校验与提参结果**：`success`

**提参结果**：

```json
{
  "operationType": "修改授权策略",
  "policyList": [
    {
      "policyId": "7d8c7b00-3c8c-4f8e-9b1e-9b17b6a3e5c3",
      "newValidityPeriod": "永久生效"
    },
    {
      "policyId": "a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
      "newValidityPeriod": "永久生效"
    }
  ]
}
```

**校验理由（LLM reasoning）**：操作类型为“修改授权策略”，在枚举范围内；模板正文规定修改操作仅支持修改有效期，并需策略标识定位策略。输入中两个条目均提供策略标识和有效期（永久生效），无格式错误、无冲突，其他不可修改字段未提供不构成缺失。按参数模式提取 operationType 与 policyList 中的 policyId、newValidityPeriod。

**键名泄漏扫描**：无泄漏

**判定**：match=True（client_prompt=True）

---

## c4-nl-del-01  —  ✅ match

### 客户端（生成）

**原始输入**（from_text）

> 删除7d8c7b00-3c8c-4f8e-9b1e-9b17b6a3e5c3这个授权策略

**生成结果：成功（渲染后 prompt）**

```
## 授权策略的操作类型
删除授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
1. 策略标识是7d8c7b00-3c8c-4f8e-9b1e-9b17b6a3e5c3

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

**提参理由（LLM reasoning）**：输入中“删除”是“删除授权策略”的简写，依据封闭取值范围规则13a直接映射为合法取值。动网操作的授权策略列表在删除操作下必填，删除形要求按策略标识删除；输入中的UUID“7d8c7b00-3c8c-4f8e-9b1e-9b17b6a3e5c3”即为策略标识，按“1. 策略标识是X”格式提取为条目，因此无缺失或非法值错误。

### 服务端（校验 + 提参）

**validateAndDataFilling 输入 schema（suite 默认 param-schema.json）**：

```json
{
  "type": "object",
  "properties": {
    "operationType": {
      "type": "string",
      "enum": [
        "新增授权策略",
        "修改授权策略",
        "删除授权策略",
        "查询授权策略"
      ],
      "description": "授权策略的操作类型"
    },
    "policyList": {
      "type": "array",
      "description": "动网操作的授权策略列表，按输入中的出现顺序排列；未提供时为空列表",
      "items": {
        "type": "object",
        "properties": {
          "policyId": {
            "type": "string",
            "description": "策略的唯一标识"
          },
          "scene": {
            "type": "string",
            "description": "动网操作支撑的业务场景"
          },
          "actionType": {
            "type": "string",
            "description": "动网操作的处置类型"
          },
          "operationName": {
            "type": "string",
            "description": "所授权的动网操作的具体名称"
          },
          "validityPeriod": {
            "type": "string",
            "description": "授权策略的生效时间区间"
          }
        }
      }
    }
  }
}
```

（输入 prompt 即上方客户端渲染结果，此处不重复。）

**校验与提参结果**：`success`

**提参结果**：

```json
{
  "operationType": "删除授权策略",
  "policyList": [
    {
      "policyId": "7d8c7b00-3c8c-4f8e-9b1e-9b17b6a3e5c3",
      "scene": null,
      "actionType": null,
      "operationName": null,
      "validityPeriod": null
    }
  ]
}
```

**校验理由（LLM reasoning）**：（未采集）

**键名泄漏扫描**：无泄漏

**判定**：match=True（client_prompt=True）

---

## c5-nl-query-01  —  ✅ match

### 客户端（生成）

**原始输入**（from_text）

> 列出所有授权策略

**生成结果：成功（渲染后 prompt）**

```
## 授权策略的操作类型
查询授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

**提参理由（LLM reasoning）**：输入中的“列出”属于查询授权策略的认可同义表达，因此操作类型映射为“查询授权策略”；当前为查询操作，策略列表选填，且输入未提供带字段标签的查询条件，因此列表值为 null，无错误。

### 服务端（校验 + 提参）

**validateAndDataFilling 输入 schema（suite 默认 param-schema.json）**：

```json
{
  "type": "object",
  "properties": {
    "operationType": {
      "type": "string",
      "enum": [
        "新增授权策略",
        "修改授权策略",
        "删除授权策略",
        "查询授权策略"
      ],
      "description": "授权策略的操作类型"
    },
    "policyList": {
      "type": "array",
      "description": "动网操作的授权策略列表，按输入中的出现顺序排列；未提供时为空列表",
      "items": {
        "type": "object",
        "properties": {
          "policyId": {
            "type": "string",
            "description": "策略的唯一标识"
          },
          "scene": {
            "type": "string",
            "description": "动网操作支撑的业务场景"
          },
          "actionType": {
            "type": "string",
            "description": "动网操作的处置类型"
          },
          "operationName": {
            "type": "string",
            "description": "所授权的动网操作的具体名称"
          },
          "validityPeriod": {
            "type": "string",
            "description": "授权策略的生效时间区间"
          }
        }
      }
    }
  }
}
```

（输入 prompt 即上方客户端渲染结果，此处不重复。）

**校验与提参结果**：`success`

**提参结果**：

```json
{
  "operationType": "查询授权策略",
  "policyList": []
}
```

**校验理由（LLM reasoning）**：操作类型章节参数值行为“查询授权策略”，属于参数schema枚举范围；动网操作的授权策略列表章节标题下无参数值行，且该列表在查询操作下为选填，因此不构成缺失。params按参数schema提取operationType与policyList，policyList为空列表。

**键名泄漏扫描**：无泄漏

**判定**：match=True（client_prompt=True）

---

## b4-nl-bad-id-01  —  ✅ match

### 客户端（生成）

**原始输入**（from_text）

> 删除ABC-123这个授权策略

**生成结果：成功（渲染后 prompt）**

```
## 授权策略的操作类型
删除授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
1. 策略标识是ABC-123

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

**提参理由（LLM reasoning）**：输入包含“删除”，按封闭取值范围规则13a映射为“删除授权策略”。删除操作下策略列表为必填，输入给出策略对象“ABC-123”，按删除形格式整理为“1. 策略标识是ABC-123”。其余字段无需在删除形中补充，因此无错误。

### 服务端（校验 + 提参）

**validateAndDataFilling 输入 schema（suite 默认 param-schema.json）**：

```json
{
  "type": "object",
  "properties": {
    "operationType": {
      "type": "string",
      "enum": [
        "新增授权策略",
        "修改授权策略",
        "删除授权策略",
        "查询授权策略"
      ],
      "description": "授权策略的操作类型"
    },
    "policyList": {
      "type": "array",
      "description": "动网操作的授权策略列表，按输入中的出现顺序排列；未提供时为空列表",
      "items": {
        "type": "object",
        "properties": {
          "policyId": {
            "type": "string",
            "description": "策略的唯一标识"
          },
          "scene": {
            "type": "string",
            "description": "动网操作支撑的业务场景"
          },
          "actionType": {
            "type": "string",
            "description": "动网操作的处置类型"
          },
          "operationName": {
            "type": "string",
            "description": "所授权的动网操作的具体名称"
          },
          "validityPeriod": {
            "type": "string",
            "description": "授权策略的生效时间区间"
          }
        }
      }
    }
  }
}
```

（输入 prompt 即上方客户端渲染结果，此处不重复。）

**校验与提参结果**：`success`

**提参结果**：

```json
{
  "operationType": "删除授权策略",
  "policyList": [
    {
      "policyId": "ABC-123",
      "scene": null,
      "actionType": null,
      "operationName": null,
      "validityPeriod": null
    }
  ]
}
```

**校验理由（LLM reasoning）**：操作类型为“删除授权策略”，在枚举范围内；删除操作仅要求提供授权策略标识，动网操作的授权策略列表已提供一条标识“ABC-123”，格式与语义均符合要求；按参数 schema 提取参数，未提供的其他策略字段置为 null。

**键名泄漏扫描**：无泄漏

**判定**：match=True（client_prompt=True）

---

## b3-nl-invalid-mod-01  —  ✅ match

### 客户端（生成）

**原始输入**（from_text）

> 把7d8c7b00-3c8c-4f8e-9b1e-9b17b6a3e5c3这个授权的业务场景改成校园专网

**生成结果：成功（渲染后 prompt）**

```
## 授权策略的操作类型
修改授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
1. 策略标识是7d8c7b00-3c8c-4f8e-9b1e-9b17b6a3e5c3，业务场景改成校园专网

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

**提参理由（LLM reasoning）**：操作类型：输入中的“改成”属于修改授权策略的同义表达，按约束映射为“修改授权策略”。动网操作的授权策略列表：用户明确指定策略标识 UUID，并要求将业务场景改为“校园专网”，属于修改类描述，按规则合并标识与修改描述，组织为单条编号条目。

### 服务端（校验 + 提参）

**validateAndDataFilling 输入 schema（suite 默认 param-schema.json）**：

```json
{
  "type": "object",
  "properties": {
    "operationType": {
      "type": "string",
      "enum": [
        "新增授权策略",
        "修改授权策略",
        "删除授权策略",
        "查询授权策略"
      ],
      "description": "授权策略的操作类型"
    },
    "policyList": {
      "type": "array",
      "description": "动网操作的授权策略列表，按输入中的出现顺序排列；未提供时为空列表",
      "items": {
        "type": "object",
        "properties": {
          "policyId": {
            "type": "string",
            "description": "策略的唯一标识"
          },
          "scene": {
            "type": "string",
            "description": "动网操作支撑的业务场景"
          },
          "actionType": {
            "type": "string",
            "description": "动网操作的处置类型"
          },
          "operationName": {
            "type": "string",
            "description": "所授权的动网操作的具体名称"
          },
          "validityPeriod": {
            "type": "string",
            "description": "授权策略的生效时间区间"
          }
        }
      }
    }
  }
}
```

（输入 prompt 即上方客户端渲染结果，此处不重复。）

**校验与提参结果**：`validation_semantic_rejected`

```json
[
  {
    "slot_name": "policyList",
    "code": "invalid_value",
    "message": "动网操作的授权策略列表第1条：修改授权策略时仅支持修改有效期，不支持修改业务场景；该条目表达将业务场景修改为“校园专网”的意图，违反约束。"
  },
  {
    "slot_name": "policyList",
    "code": "missing_required",
    "message": "动网操作的授权策略列表第1条：修改授权策略时缺少必填的新有效期信息。"
  }
]
```

**校验理由（LLM reasoning）**：（未采集）

**键名泄漏扫描**：无泄漏

**判定**：match=True（client_prompt=True）

---

## b2-nl-format-01  —  ✅ match

### 客户端（生成）

**原始输入**（from_text）

> 新增授权，场景校园专网，处置业务抢通，操作隧道调优，有效期2026-13-01~2030-12-31

**生成结果：成功（渲染后 prompt）**

```
## 授权策略的操作类型
新增授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
1. 业务场景是校园专网，处置类型是业务抢通，操作名称是隧道调优，有效期是2026-13-01~2030-12-31

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

**提参理由（LLM reasoning）**：操作类型：输入含“新增”，属于封闭取值范围认可的简写，映射为“新增授权策略”。策略列表：操作类型为新增授权策略时该列表必填；输入中“场景校园专网”对应业务场景，“处置业务抢通”对应处置类型，“操作隧道调优”对应操作名称，“有效期2026-13-01~2030-12-31”对应有效期。按新增形格式组织为编号条目并保留原日期内容，月份异常值按规则16原样提取，不由提取阶段判定为invalid_value。

### 服务端（校验 + 提参）

**validateAndDataFilling 输入 schema（suite 默认 param-schema.json）**：

```json
{
  "type": "object",
  "properties": {
    "operationType": {
      "type": "string",
      "enum": [
        "新增授权策略",
        "修改授权策略",
        "删除授权策略",
        "查询授权策略"
      ],
      "description": "授权策略的操作类型"
    },
    "policyList": {
      "type": "array",
      "description": "动网操作的授权策略列表，按输入中的出现顺序排列；未提供时为空列表",
      "items": {
        "type": "object",
        "properties": {
          "policyId": {
            "type": "string",
            "description": "策略的唯一标识"
          },
          "scene": {
            "type": "string",
            "description": "动网操作支撑的业务场景"
          },
          "actionType": {
            "type": "string",
            "description": "动网操作的处置类型"
          },
          "operationName": {
            "type": "string",
            "description": "所授权的动网操作的具体名称"
          },
          "validityPeriod": {
            "type": "string",
            "description": "授权策略的生效时间区间"
          }
        }
      }
    }
  }
}
```

（输入 prompt 即上方客户端渲染结果，此处不重复。）

**校验与提参结果**：`validation_semantic_rejected`

```json
[
  {
    "slot_name": "policyList",
    "code": "format_error",
    "message": "策略列表第1条的有效期月份无效：2026-13-01 不是有效日期，月份应为1~12。"
  }
]
```

**校验理由（LLM reasoning）**：（未采集）

**键名泄漏扫描**：无泄漏

**判定**：match=True（client_prompt=True）

---

## b2-nl-format-01-varreq  —  ✅ match
[【差分对】配对基线：b2-nl-format-01]

### 客户端（生成）

**原始输入**（from_text）

> 新增一个授权，业务场景校园专网，处置类型网络出了问题要赶紧恢复，操作名称频段调整，有效期2026-01-01~2030-12-31

**生成结果：成功（渲染后 prompt）**

```
## 授权策略的操作类型
新增授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
1. 业务场景是校园专网，处置类型是网络出了问题要赶紧恢复，操作名称是频段调整，有效期是2026-01-01~2030-12-31

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

**提参理由（LLM reasoning）**：输入“新增一个授权”中的“新增”命中操作类型合法简写，映射为“新增授权策略”。操作类型为新增时，授权策略列表为必填；输入依次给出业务场景、处置类型、操作名称、有效期，按新增形字段顺序整理为单条编号条目。日期已符合YYYY-MM-DD~YYYY-MM-DD格式，无需转换。

### 服务端（校验 + 提参）

**validateAndDataFilling 输入 schema（本用例变异 schema）**：

```json
{
  "type": "object",
  "properties": {
    "operationType": {
      "type": "string",
      "enum": [
        "新增授权策略",
        "修改授权策略",
        "删除授权策略",
        "查询授权策略"
      ],
      "description": "授权策略的操作类型"
    },
    "policyList": {
      "type": "array",
      "description": "动网操作的授权策略列表，按输入中的出现顺序排列；未提供时为空列表",
      "items": {
        "type": "object",
        "properties": {
          "policyId": {
            "type": "string",
            "description": "策略的唯一标识"
          },
          "scene": {
            "type": "string",
            "description": "动网操作支撑的业务场景"
          },
          "actionType": {
            "type": "string",
            "description": "动网操作的处置类型，应为简短的处置动作短语（如业务抢通、业务优化、路由调整），非短语式的整句描述判 invalid_value"
          },
          "operationName": {
            "type": "string",
            "description": "所授权的动网操作的具体名称"
          },
          "validityPeriod": {
            "type": "string",
            "description": "授权策略的生效时间区间，YYYY-MM-DD~YYYY-MM-DD格式或\"永久生效\"；须为完整区间（仅单个日期视为区间不完整），且起始日期必须早于结束日期"
          }
        }
      }
    }
  }
}
```

（输入 prompt 即上方客户端渲染结果，此处不重复。）

**校验与提参结果**：`validation_semantic_rejected`

```json
[
  {
    "slot_name": "policyList",
    "code": "invalid_value",
    "message": "动网操作的授权策略列表第1条的处置类型应为简短处置动作短语（如业务抢通、业务优化、路由调整），实际为整句描述“网络出了问题要赶紧恢复”，不符合取值约束。"
  }
]
```

**校验理由（LLM reasoning）**：（未采集）

**键名泄漏扫描**：无泄漏

**判定**：match=True（client_prompt=True）

---

## b1-nl-missing-01  —  ✅ match

### 客户端（生成）

**原始输入**（from_text）

> 加个校园专网的授权，处置用业务抢通，做个隧道调优，有效期先不填后面补

**生成结果：成功（渲染后 prompt）**

```
## 授权策略的操作类型
新增授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
1. 业务场景是校园专网，处置类型是业务抢通，操作名称是隧道调优

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

**提参理由（LLM reasoning）**：操作类型：输入中的“加个”属于认可同义对清单（加/加个→新增授权策略），因此提取为“新增授权策略”。策略列表：输入提供了业务场景（校园专网）、处置类型（业务抢通）、操作名称（隧道调优）三个正向约束，按约定格式组织为编号条目；有效期用户明确表示“先不填后面补”，属于缺省声明，不提取为该字段值，因此省略该字段及分隔符。未发现需要报告的 slot 错误。

### 服务端（校验 + 提参）

**validateAndDataFilling 输入 schema（suite 默认 param-schema.json）**：

```json
{
  "type": "object",
  "properties": {
    "operationType": {
      "type": "string",
      "enum": [
        "新增授权策略",
        "修改授权策略",
        "删除授权策略",
        "查询授权策略"
      ],
      "description": "授权策略的操作类型"
    },
    "policyList": {
      "type": "array",
      "description": "动网操作的授权策略列表，按输入中的出现顺序排列；未提供时为空列表",
      "items": {
        "type": "object",
        "properties": {
          "policyId": {
            "type": "string",
            "description": "策略的唯一标识"
          },
          "scene": {
            "type": "string",
            "description": "动网操作支撑的业务场景"
          },
          "actionType": {
            "type": "string",
            "description": "动网操作的处置类型"
          },
          "operationName": {
            "type": "string",
            "description": "所授权的动网操作的具体名称"
          },
          "validityPeriod": {
            "type": "string",
            "description": "授权策略的生效时间区间"
          }
        }
      }
    }
  }
}
```

（输入 prompt 即上方客户端渲染结果，此处不重复。）

**校验与提参结果**：`validation_semantic_rejected`

```json
[
  {
    "slot_name": "policyList",
    "code": "missing_required",
    "message": "动网操作的授权策略列表第1条缺少有效期：新增授权策略要求每条提供动网操作的业务场景、动网操作的处置类型、动网操作名称、有效期。"
  }
]
```

**校验理由（LLM reasoning）**：操作类型为新增授权策略，取值合法。动网操作的授权策略列表提供1条，已提供业务场景、处置类型、操作名称，但未提供有效期；新增授权策略下有效期属于必填字段，因此判定语义校验失败并报告missing_required。policyId在创建场景下非必填，不报错。参数提取按实际提供的参数值填充，缺失的policyId和validityPeriod输出null。

**键名泄漏扫描**：无泄漏

**判定**：match=True（client_prompt=True）

---

## c6-nl-mixed-07  —  ✅ match

### 客户端（生成）

**原始输入**（from_text）

> 新增三条授权：第一条业务场景校园专网，处置类型业务抢通，操作名称隧道调优，有效期永久生效；第二条业务场景医疗专线，处置类型业务恢复，操作名称频段调整，有效期2026-13-01~2030-12-31；第三条业务场景政企专网，处置类型业务优化，操作名称路由调整，有效期2027-03-15~2029-09-30

**生成结果：成功（渲染后 prompt）**

```
## 授权策略的操作类型
新增授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
1. 业务场景是校园专网，处置类型是业务抢通，操作名称是隧道调优，有效期是永久生效
2. 业务场景是医疗专线，处置类型是业务恢复，操作名称是频段调整，有效期是2026-13-01~2030-12-31
3. 业务场景是政企专网，处置类型是业务优化，操作名称是路由调整，有效期是2027-03-15~2029-09-30

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

**提参理由（LLM reasoning）**：输入中出现“新增”，按封闭取值范围映射为“新增授权策略”。动网操作的授权策略列表按三条进行编号并补全为“字段名是值”格式；有效期“2026-13-01~2030-12-31”虽含无效月份，但按规则16对非封闭取值范围槽位原样提取，不报invalid_value。

### 服务端（校验 + 提参）

**validateAndDataFilling 输入 schema（suite 默认 param-schema.json）**：

```json
{
  "type": "object",
  "properties": {
    "operationType": {
      "type": "string",
      "enum": [
        "新增授权策略",
        "修改授权策略",
        "删除授权策略",
        "查询授权策略"
      ],
      "description": "授权策略的操作类型"
    },
    "policyList": {
      "type": "array",
      "description": "动网操作的授权策略列表，按输入中的出现顺序排列；未提供时为空列表",
      "items": {
        "type": "object",
        "properties": {
          "policyId": {
            "type": "string",
            "description": "策略的唯一标识"
          },
          "scene": {
            "type": "string",
            "description": "动网操作支撑的业务场景"
          },
          "actionType": {
            "type": "string",
            "description": "动网操作的处置类型"
          },
          "operationName": {
            "type": "string",
            "description": "所授权的动网操作的具体名称"
          },
          "validityPeriod": {
            "type": "string",
            "description": "授权策略的生效时间区间"
          }
        }
      }
    }
  }
}
```

（输入 prompt 即上方客户端渲染结果，此处不重复。）

**校验与提参结果**：`validation_semantic_rejected`

```json
[
  {
    "slot_name": "policyList",
    "code": "format_error",
    "message": "动网操作的授权策略列表第2条的有效期“2026-13-01~2030-12-31”月份无效，13月不存在，应为1~12月。"
  }
]
```

**校验理由（LLM reasoning）**：操作类型“新增授权策略”在schema枚举范围内，合规。新增授权策略时，policyList每条必须包含业务场景、处置类型、操作名称、有效期；第1条和第3条均满足要求，第2条有效期月份为13，属于无效日期，构成format_error，因此semantic_verdict为false。参数提取按输入顺序完整提取三条策略，未提供的policyId按要求输出null。

**键名泄漏扫描**：无泄漏

**判定**：match=True（client_prompt=True）

---

## a-data-starve-01  —  ✅ match

### 客户端（生成）

**原始输入**（from_data_with_schema）

data:
```json
{
  "scene": "校园专网",
  "actionType": "业务抢通"
}
```
input.schema:
```json
{
  "type": "object",
  "properties": {
    "scene": {
      "type": "string",
      "description": "动网操作支撑的业务场景"
    },
    "actionType": {
      "type": "string",
      "description": "动网操作的处置类型"
    }
  }
}
```

**生成结果：失败**

```json
{
  "code": "slot_validation_error",
  "message": "Required slots are missing or empty: 授权策略的操作类型",
  "slot_errors": [
    {
      "slot_name": "授权策略的操作类型",
      "code": "missing_required",
      "message": "Required slot is missing or empty"
    }
  ]
}
```

**提参理由（LLM reasoning）**：输入中未出现“新增/修改/删除/查询”等操作类型合法取值或其认可同义对，因此“授权策略的操作类型”置为null并报告missing_input。“scene=校园专网”和“actionType=业务抢通”分别对应策略列表中的业务场景和处置类型字段，属于非封闭列表slot，按规则提取并格式化为编号条目。

### 服务端（校验 + 提参）

（客户端生成失败路径，服务端未被调用。）

**判定**：match=True（client_prompt=None）

---

## a-nl-neg-01  —  ✅ match

### 客户端（生成）

**原始输入**（from_text）

> 不要新增校园专网的授权策略，我还没准备好

**生成结果：失败**

```json
{
  "code": "slot_validation_error",
  "message": "Required slots are missing or empty: 授权策略的操作类型",
  "slot_errors": [
    {
      "slot_name": "授权策略的操作类型",
      "code": "missing_required",
      "message": "Required slot is missing or empty"
    }
  ]
}
```

**提参理由（LLM reasoning）**：输入为否定语义“不要新增校园专网的授权策略，我还没准备好”，其中“新增”处于排除/否定语境，不能作为正向的操作类型值提取；“校园专网”等列表信息同样处于否定语义中，不提取。操作类型为必填槽且未提取到有效值，因此报告 missing_input；策略列表为可选槽，且操作类型未确定为新增/修改/删除时不做条件必填判定，置为 null 且不报错。

### 服务端（校验 + 提参）

（客户端生成失败路径，服务端未被调用。）

**判定**：match=True（client_prompt=None）

---

## 差分对判读汇总

| 差分对 | 基线半 | 变异半 | 判读 |
|---|---|---|---|
| c1-nl-add-01/-varname | ✅ | ✅ | 健康 |
| c3-nl-mod-06/-varfields | ✅ | ✅ | 健康 |
| b2-nl-format-01/-varreq | ✅ | ✅ | 健康 |
