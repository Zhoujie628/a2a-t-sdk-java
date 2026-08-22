# Negotiation-T 六个接口示例

本文使用“传输专线业务投诉诊断”场景，展示 3 个自然语言生成接口和 3 个校验/提参接口的调用方法。

完整链路分为两条：

- 接受：Propose 生成 → Propose 校验/提参 → Accept 生成 → Accept 校验/提参。
- 拒绝：Propose 生成 → Propose 校验/提参 → Reject 生成 → Reject 校验/提参。

Accept 与 Reject 是互斥结果。单次协商调用 4 个接口；分别运行接受和拒绝两条链路，覆盖全部 6 个接口。

## 1. 公共对象

以下示例假定 `A2ATClient` 和 `A2ATServer` 已按照 Sample 的环境配置完成初始化：

```java
A2ATClient client = ...;
A2ATServer server = ...;
```

公共模板 URI 和协商上下文：

```java
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;

TemplateUri proposeTemplateUri = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;
TemplateUri endingTemplateUri = StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT;

NegotiationContext context = new NegotiationContext(
        "5dd96c7b-2f19-4885-93d7-31e8bffac25c",
        1,
        3);
```

本场景的业务 Schema 已由 Sample 提供：

```java
Map<String, Object> proposeSchema = InformationNegotiationSchemas.propose();
Map<String, Object> acceptSchema = InformationNegotiationSchemas.accept();
Map<String, Object> rejectSchema = InformationNegotiationSchemas.reject();
```

这三个方法只是复用入口，实际传入接口的 Schema 如下。

### 1.1 Propose Schema 示例

Propose 阶段需要从协商请求中提取“希望对端补充什么信息，以及信息应满足什么要求”：

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "access_port_name": {
      "type": "string",
      "description": "请求的接入端口信息及可接受的物理或逻辑端口格式"
    },
    "complaint_category": {
      "type": "string",
      "description": "请求的投诉分类及允许的专线投诉类型"
    }
  },
  "required": ["access_port_name", "complaint_category"]
}
```

对应当前场景的预期提取值：

```json
{
  "access_port_name": "物理端口或逻辑端口名称",
  "complaint_category": "专线中断或专线质差"
}
```

### 1.2 Accept Schema 示例

Accept 阶段需要提取对端实际补充的端口和投诉分类：

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "access_port_name": {
      "type": "string",
      "description": "用于专线诊断的物理或逻辑接入端口名称"
    },
    "complaint_category": {
      "type": "string",
      "description": "专线投诉分类",
      "enum": ["专线中断", "专线质差"]
    }
  },
  "required": ["access_port_name", "complaint_category"]
}
```

对应当前场景的预期提取值：

```json
{
  "access_port_name": "P781-珠江新城-PTN7900-23-TPA1EG24-17(cvlan=100)",
  "complaint_category": "专线质差"
}
```

### 1.3 Reject Schema 示例

Reject 消息没有端口和投诉分类的实际值，因此提取拒绝原因：

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "rejection_reason": {
      "type": "string",
      "description": "无法补充专线投诉信息的原因"
    }
  },
  "required": ["rejection_reason"]
}
```

对应当前场景的预期提取值：

```json
{
  "rejection_reason": "当前账号没有资源系统查询权限"
}
```

### 1.4 等价的 Java Map 示例

以 Propose Schema 为例，不通过 Sample 工厂类时，可以直接这样构造：

```java
Map<String, Object> proposeSchema = Map.of(
        "type", "object",
        "additionalProperties", false,
        "properties", Map.of(
                "access_port_name", Map.of(
                        "type", "string",
                        "description", "请求的接入端口信息及可接受的物理或逻辑端口格式"),
                "complaint_category", Map.of(
                        "type", "string",
                        "description", "请求的投诉分类及允许的专线投诉类型")),
        "required", List.of("access_port_name", "complaint_category"));

FilledParamData proposeData = server.validateAndFillingProposeData(
        receivedProposePrompt,
        proposeSchema,
        proposeTemplateUri);
```

Sample 中的 `InformationNegotiationSchemas` 将上述结构集中管理，避免在每个调用点重复构造。

三个 Schema 的提取目标汇总如下：

| 阶段 | 业务字段 |
| --- | --- |
| Propose | `access_port_name`、`complaint_category` |
| Accept | `access_port_name`、`complaint_category` |
| Reject | `rejection_reason` |

`id`、`round`、`maxRounds` 由 SDK 从 Negotiation-T Prompt 中确定性提取，不需要写入业务 Schema。

## 2. Propose 生成接口

接口：

```java
MetadataContent generateNegotiationProposePromptFromText(
        String text,
        NegotiationContext context,
        TemplateUri templateUri)
```

调用示例：

```java
String proposeText = "当前专线业务投诉诊断缺少接入端口名称和投诉分类，"
        + "请发起信息协商，请求对端补充这两项信息。"
        + "接入端口名称需要提供物理端口或逻辑端口名称，"
        + "投诉分类应为专线中断或专线质差。";

MetadataContent propose = client.generateNegotiationProposePromptFromText(
        proposeText,
        context,
        proposeTemplateUri);
```

`MetadataContent` 中包含：

- `templateUri()`：`Negotiation-T/information-negotiation/propose/v1`。
- `promptText()`：SDK 根据自然语言、上下文和内置模板生成的完整 Propose Prompt。
- `extensionUri()`：Negotiation-T 的 TMF 扩展 URI。
- `buildMetadataContent()`：用于放入 A2A `Message.metadata` 的两个键值。

发送端应使用以下结果作为线上消息 metadata：

```java
Map<String, String> messageMetadata = propose.buildMetadataContent();
```

metadata 的结构为：

```json
{
  "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1": "<完整 Propose Prompt>",
  "templateUri": "Negotiation-T/information-negotiation/propose/v1"
}
```

## 3. Propose 校验和提参接口

接口：

```java
FilledParamData validateAndFillingProposeData(
        String prompt,
        Map<String, Object> schema,
        TemplateUri templateUri)
```

接收端应从 A2A `Message.metadata` 读取 Prompt，不能使用原始自然语言 `proposeText` 代替：

```java
String receivedProposePrompt = NegotiationMetadataReader.readPrompt(
        messageMetadata,
        proposeTemplateUri);

FilledParamData proposeData = server.validateAndFillingProposeData(
        receivedProposePrompt,
        proposeSchema,
        proposeTemplateUri);

Map<String, Object> proposeParams = proposeData.data();
```

本场景在 Mock 模式下的预期结果：

```json
{
  "id": "5dd96c7b-2f19-4885-93d7-31e8bffac25c",
  "round": 1,
  "maxRounds": 3,
  "access_port_name": "物理端口或逻辑端口名称",
  "complaint_category": "专线中断或专线质差"
}
```

服务端使用校验结果中的上下文字段构造回复上下文，不能生成新的协商 ID：

```java
NegotiationContext responseContext = new NegotiationContext(
        (String) proposeParams.get("id"),
        ((Number) proposeParams.get("round")).intValue(),
        ((Number) proposeParams.get("maxRounds")).intValue());
```

## 4. Accept 生成接口

接口：

```java
MetadataContent generateNegotiationAcceptPromptFromText(
        String text,
        NegotiationContext context,
        TemplateUri templateUri)
```

调用示例：

```java
String acceptText = "接受本次信息协商。"
        + "接入端口名称为 P781-珠江新城-PTN7900-23-TPA1EG24-17(cvlan=100)，"
        + "投诉分类为专线质差。";

MetadataContent accept = server.generateNegotiationAcceptPromptFromText(
        acceptText,
        responseContext,
        endingTemplateUri);

Map<String, String> artifactMetadata = accept.buildMetadataContent();
```

该 metadata 应放入返回给发起方的 A2A `Artifact.metadata`。其中 `templateUri` 为：

```text
Negotiation-T/information-negotiation/accept-reject/v1
```

## 5. Accept 校验和提参接口

接口：

```java
FilledParamData validateAndFillingAcceptData(
        String prompt,
        Map<String, Object> schema,
        TemplateUri templateUri)
```

调用示例：

```java
String receivedAcceptPrompt = NegotiationMetadataReader.readPrompt(
        artifactMetadata,
        endingTemplateUri);

FilledParamData acceptData = client.validateAndFillingAcceptData(
        receivedAcceptPrompt,
        acceptSchema,
        endingTemplateUri);

Map<String, Object> acceptParams = acceptData.data();
```

本场景在 Mock 模式下的预期结果：

```json
{
  "id": "5dd96c7b-2f19-4885-93d7-31e8bffac25c",
  "round": 1,
  "maxRounds": 3,
  "access_port_name": "P781-珠江新城-PTN7900-23-TPA1EG24-17(cvlan=100)",
  "complaint_category": "专线质差"
}
```

## 6. Reject 生成接口

接口：

```java
MetadataContent generateNegotiationRejectPromptFromText(
        String text,
        NegotiationContext context,
        TemplateUri templateUri)
```

Reject 与 Accept 使用同一个 `accept-reject` 模板 URI：

```java
String rejectText = "拒绝本次信息协商。"
        + "当前账号没有资源系统查询权限，"
        + "无法取得接入端口名称和投诉分类。";

MetadataContent reject = server.generateNegotiationRejectPromptFromText(
        rejectText,
        responseContext,
        endingTemplateUri);

Map<String, String> artifactMetadata = reject.buildMetadataContent();
```

## 7. Reject 校验和提参接口

接口：

```java
FilledParamData validateAndFillingRejectData(
        String prompt,
        Map<String, Object> schema,
        TemplateUri templateUri)
```

调用示例：

```java
String receivedRejectPrompt = NegotiationMetadataReader.readPrompt(
        artifactMetadata,
        endingTemplateUri);

FilledParamData rejectData = client.validateAndFillingRejectData(
        receivedRejectPrompt,
        rejectSchema,
        endingTemplateUri);

Map<String, Object> rejectParams = rejectData.data();
```

本场景在 Mock 模式下的预期结果：

```json
{
  "id": "5dd96c7b-2f19-4885-93d7-31e8bffac25c",
  "round": 1,
  "maxRounds": 3,
  "rejection_reason": "当前账号没有资源系统查询权限"
}
```

## 8. 两条完整调用链

接受分支：

```java
MetadataContent propose = client.generateNegotiationProposePromptFromText(
        proposeText, context, proposeTemplateUri);
String proposePrompt = NegotiationMetadataReader.readPrompt(
        propose.buildMetadataContent(), proposeTemplateUri);
FilledParamData proposeData = server.validateAndFillingProposeData(
        proposePrompt, proposeSchema, proposeTemplateUri);

NegotiationContext responseContext = new NegotiationContext(
        (String) proposeData.data().get("id"),
        ((Number) proposeData.data().get("round")).intValue(),
        ((Number) proposeData.data().get("maxRounds")).intValue());

MetadataContent accept = server.generateNegotiationAcceptPromptFromText(
        acceptText, responseContext, endingTemplateUri);
String acceptPrompt = NegotiationMetadataReader.readPrompt(
        accept.buildMetadataContent(), endingTemplateUri);
FilledParamData acceptData = client.validateAndFillingAcceptData(
        acceptPrompt, acceptSchema, endingTemplateUri);
```

拒绝分支的前 3 步相同，结束阶段替换为：

```java
MetadataContent reject = server.generateNegotiationRejectPromptFromText(
        rejectText, responseContext, endingTemplateUri);
String rejectPrompt = NegotiationMetadataReader.readPrompt(
        reject.buildMetadataContent(), endingTemplateUri);
FilledParamData rejectData = client.validateAndFillingRejectData(
        rejectPrompt, rejectSchema, endingTemplateUri);
```

## 9. 调用时需要核对的约束

- 生成接口的 `text` 是业务自然语言；校验接口的 `prompt` 是生成接口返回的完整 `promptText`。
- Propose 使用 `information-negotiation/propose`；Accept 和 Reject 使用 `information-negotiation/accept-reject`。
- 校验接口的 `TemplateUri` 应与 metadata 中的 `templateUri` 字符串一致；需要比较时使用 `templateUri.uri()`。
- 回复必须沿用 Propose 中的 `id`、`round` 和 `maxRounds`。
- 业务 Schema 跟具体 OSS 场景绑定，本示例的字段只适用于当前专线投诉场景。
- Mock 模式的结果是确定的；Qwen3-32B 模式下自然语言表述可能变化，但结构、上下文和 Schema 约束应保持一致。
