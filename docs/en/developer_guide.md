# 1 a2a-t-sdk-java Developer Guide

## 1.1 Feature Introduction

See the [User Guide 1.1 Feature Introduction](https://github.com/project-openan/a2a-t-sdk-java/blob/main/docs/en/user_guide.md#11-feature-introduction) section.

## 1.2 Constraints and Limitations

1. JDK requirement is 17+.
2. Currently supports `classpath` and `local_file` prompt resources. The default source is the built-in resources packaged in the `a2a-t-resources` jar.
3. By default supports `openai` LLM provider.
4. Negotiation state storage currently supports `in_memory`.
5. The SDK is not responsible for starting business HTTP services, user authentication, key management, or registry center deployment.

## 1.3 Environment Setup

### 1.3.1 Get Source Code

```bash
git clone git@github.com:project-openan/a2a-t-sdk-java.git
cd a2a-t-sdk-java
```

### 1.3.2 Build Project

```bash
mvn -DskipTests package
```

### 1.3.3 Run Tests

```bash
mvn test
```

### 1.3.4 Format Check

The project uses Spotless to manage Java formatting:

```bash
mvn spotless:check
```

For auto-formatting:

```bash
mvn spotless:apply
```

## 1.4 Maven Dependencies

Business projects can use BOM to manage versions:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>net.openan.a2a-t.sdk</groupId>
            <artifactId>a2a-t-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Client Agent dependency:

```xml
<dependency>
    <groupId>net.openan.a2a-t.sdk</groupId>
    <artifactId>a2a-t-client</artifactId>
</dependency>
```

Server Agent dependency:

```xml
<dependency>
    <groupId>net.openan.a2a-t.sdk</groupId>
    <artifactId>a2a-t-server</artifactId>
</dependency>
```

If the business system needs to directly read SDK built-in resources, or wants to use `A2AT_PROMPT_SOURCE_TYPE=classpath` without relying on transitive dependencies, add the following dependency:

```xml
<dependency>
    <groupId>net.openan.a2a-t.sdk</groupId>
    <artifactId>a2a-t-resources</artifactId>
</dependency>
```

## 1.5 Configuration Loading

The Java SDK does not auto-discover `.env`; the caller must explicitly pass the path:

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.client.A2ATClient;

A2ATClient client = new A2ATClient(Path.of("client.env"));
```

Basic configuration example:

```properties
A2AT_LANGUAGE=zh-CN
A2AT_PROMPT_SOURCE_TYPE=classpath
A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
A2AT_LLM_PROVIDER=openai
A2AT_LLM_MODEL=deepseek-chat
A2AT_LLM_BASE_URL=https://api.deepseek.com
A2AT_LLM_API_KEY={your_api_key}
A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
```

For reasoning models, the optional `A2AT_LLM_REASONING_EFFORT` key tunes the reasoning effort passed to the
provider: one of `none`, `minimal`, `low`, `medium`, `high`, `xhigh` (case-insensitive). Leave the key unset for
non-reasoning models — the parameter is then not sent at all. An unsupported value fails at configuration load
time with an `LLMConfigError` instead of surfacing as a provider error on the first LLM call.


## 1.6 SDK Basic Usage

### 1.6.1 Client Generates Task Prompt

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;

A2ATClient client = new A2ATClient(Path.of("client.env"));
PromptGenerationResult result = client.generateTaskPrompt(
        "Notification topic is Incident, subscription condition: ETH-LOS fault with subscription level critical, notification data format for reporting: DataPart");

if (result.success()) {
    System.out.println(result.promptText());
} else {
    System.out.println(result.failure().message());
}
```

### 1.6.2 Server Validates Task Prompt

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.server.A2ATServer;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;

A2ATServer server = new A2ATServer(Path.of("server.env"));
PromptComplianceResult result = server.checkTaskPrompt(processedPromptText);

if (result.success()) {
    System.out.println("prompt check passed");
} else {
    System.out.println(result.failure().message());
}
```

### 1.6.3 Negotiation Interface

```java
import java.nio.file.Path;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;

A2ATClient client = new A2ATClient(Path.of("client.env"));
Map<String, Object> payload = client.startNegotiation(
        NegotiationType.INFORMATION,
        "Please provide incident level.",
        Map.of("missingFields", java.util.List.of("subscription_condition_incident_level")));
```

The business system needs to pass the text and context in the negotiation payload to the peer via A2A messages, and subsequently advance the state through `receiveNegotiation` and `continueNegotiation`.


## 1.7 Full Integration Development Flow

### 1.7.1 Client Agent

Recommended Client Agent flow:

1. Read `.env` and create `A2ATClient`.
2. Call `generateTaskPrompt` based on user input.
3. Place the prompt into the A2A message body or extension fields.
4. Query the target AgentCard directly from the server root path `GET /`, or through the registry center when registry lookup is required.
5. Send an A2A request based on the interface address in the AgentCard.
6. If negotiation context is received, continue the interaction through `receiveNegotiation` and `continueNegotiation`.

`ClientSampleFlow` in the `subscribe_incident` case of `a2a-t-sample` demonstrates the full flow: reading scenarios, querying AgentCard directly from the sample server or from registry-center, generating prompt, constructing A2A requests, and handling streaming events.

### 1.7.2 Server Agent

Recommended Server Agent flow:

1. Create a business HTTP service or integrate with an existing A2A Java service framework.
2. Construct AgentCard at startup, expose it from the service root path `GET /`, and register it with the registry center when registry lookup is used.
3. After receiving an A2A request, extract the processed task prompt.
4. Call `A2ATServer.checkTaskPrompt`.
5. After validation passes, execute business logic.
6. When validation fails or information is insufficient, return supplementary information requirements through the negotiation interface.

`ServerSampleMain` and `ServerSampleFlow` in the `subscribe_incident` case of `a2a-t-sample` demonstrate AgentCard exposure at the HTTP root path, optional registry-center registration, HTTP service startup, prompt extraction, validation, and task event pushing.

## 1.8 Prompt Resource Extension
### 1.8.1 Resource Source

Prompt resources can be loaded from two sources:

| Source Type | Description |
|--|--|
| `classpath` | Loads built-in prompt resources from the runtime classpath, typically from the `a2a-t-resources` jar downloaded by Maven. This is the default and is used by `a2a-t-sample` when `a2a-t-resources` is present in the sample `pom.xml` and runtime classpath. |
| `local_file` | Loads prompt resources from a local directory specified by `A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR`. Use this when developing or overriding scenarios, slots, templates, or LLM prompt files. |

To use the built-in resources from the jar, keep the source type as `classpath`:

```properties
A2AT_PROMPT_SOURCE_TYPE=classpath
A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
A2AT_LANGUAGE=zh-CN
```

### 1.8.2 Local File Extension
When customizing scenarios, prepare local resources with the following structure:

```text
prompt_resources/
  scenarios/zh-CN/scenarios.json
  slots/{extension_name}/{path_segments}/{version}/zh-CN/slot.json
  templates/{extension_name}/{path_segments}/{version}/zh-CN/template.md
  prompts/scenario_recognition/zh-CN/system.md
  prompts/scenario_recognition/zh-CN/user.md
  prompts/slot_extraction/zh-CN/system.md
  prompts/slot_extraction/zh-CN/user.md
  prompts/semantic_validation/zh-CN/system.md
  prompts/semantic_validation/zh-CN/user.md
```

The `slots/` and `templates/` directory paths mirror the template URI segment by segment: `{extension_name}/{path_segments}/{version}` is the template URI, with `{version}` (`v1` by default) as the trailing segment. For example, the `energy-saving` scenario resolves to `templates/Task-T/network-layer/energy-saving/v1/zh-CN/template.md` and `slots/Task-T/network-layer/energy-saving/v1/zh-CN/slot.json`; negotiation templates follow `templates/Negotiation-T/information-negotiation/propose/v1/zh-CN/template.md`.

Then specify in `.env`:

```properties
A2AT_PROMPT_SOURCE_TYPE=local_file
A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR={your_prompt_resources_root}
A2AT_LANGUAGE=zh-CN
```

It is recommended to add the following tests for new resources:

1. Scenario loading tests.
2. Slot Schema loading and validation tests.
3. Client prompt generation tests.
4. Server prompt compliance validation tests.
5. Sample-level end-to-end verification.

### 1.8.3 How to Define Prompt Templates
#### 1.8.3.1 Core Value
A2A-T Structured Prompt provides a reusable structured approach for providing clear and consistent prompts to LLMs. By separating core logic from variable data, it makes interactions between agents more reliable, efficient, and scalable. The main benefits of using Structured Prompt include:
- 	Consistency: Ensures prompts follow a standardized format, making agent output more predictable.
-	Efficiency: Avoids writing each Prompt from scratch, saving time and effort. Also avoids repeating complex instructions.
-	Scalability: Makes it easier to generate prompts for various business scenarios.
-	Optimization: Allows templates to be refined and optimized for better results.

#### 1.8.3.2 Classification of A2A-T Prompt Templates
For agent communication in the telecom domain, to ensure completeness of request content and improve reasoning efficiency and accuracy, A2A-T defines Structured Prompt templates for each AN high-value scenario, and published the industry A2A-T protocol standards at TMF:
《IG1453A_Structured_Prompt_of_Agent_to_Agent_Protocol_for_Telecoms_A2AT_v1.0.0》
《IG1453_Agent_to_Agent_Protocol_for_Telecoms_A2AT_v2.0.0》

Structured Prompt template definitions are divided into two layers:
- L0 Basic Templates:
Define the foundational framework of Structured Prompt for ICT domain tasks, without specifying variables and ontology for specific scenarios.
L0 template list:

	| Template Name | Description   |
	|--|--|
	|Task-T  | Defines the basic structure of ICT domain tasks, but does not specify commonly used variables and general ontology specifications for specific scenarios. Parsing of basic templates relies on the LLM's reasoning ability and the Agent's context processing ability. |
	|Notification-T | Defines a structured prompt-based network event subscription and reporting mechanism for the ICT domain. This mechanism ensures real-time perception of network events, and through structured prompts, provides consistent task descriptions across different levels and domains. |

- L1 Value Scenario Prompt Templates:
Building on L0 templates, commonly used "variables" are defined for different high-value scenario tasks, so that during task generation, agents can input corresponding content based on these variables, and during task execution, identify related content to improve reasoning efficiency and accuracy.

#### 1.8.3.3 Core Composition Elements
A complete A2A-T Prompt template generally contains the following two parts:
1. Instructions
	- Definition: Core directives or context.
	- Role: Provides the basic requirements and framework of the task.
	- Syntax: Use ## to mark instruction names directly (e.g., ## 任务描述).
2. Variables
	- Definition: Dynamic slots, filled with specific data each time they are used.
	- Role: Provides more specific information, improving reasoning efficiency.
	- Syntax: Use double curly braces {{}} to mark variable names (e.g., {{故障发生时间}}).

##### 1.8.3.3.1 Instructions

1. Instruction syntax requirements: When declaring "instructions", use "##" for marking, followed by the name of the "instruction", so that the Agent can recognize it and thereby implement content input or corresponding reasoning and execution.
2. Instruction set: The Structured Prompt defined by A2A-T has established the foundational framework for ICT task Prompt templates, deconstructing typical ICT task information into the following instructions.


| Instruction Name | Required/Optional | Description & Example |
|--|--|--|
| 任务描述 |	Required 	| Describes the basic requirements of the task. Example:<br> `## 任务描述` <br> `Please analyze the root cause of the fault based on "目标对象", "任务上下文", and "约束条件", and provide repair suggestions. Please respond to the task according to the structure defined in "预期输出".`
|任务类型|Optional  |Identifies the task type (e.g., fault diagnosis, energy efficiency optimization). Example:<br>`## 任务类型`<br>`Fault diagnosis `
|目标对象|Optional|  Describes the direct object of the task operation. Example:<br> `## 目标对象`<br>`Fault identifier (fault-csn) is "OSS-FAULT-20250405-001".`
|任务上下文|Optional  |Provides background information for task execution. Example:<br>`## 任务上下文`<br>`Fault occurrence time (occur-time) is "2025-04-05T14:30:00Z". `
| 预期输出|Optional  | Defines the format of expected results. Example:<br>`## 预期输出`<br> `Fault diagnosis results should include the following information: 1. Diagnosis status: success or failure 2. Fault diagnosis analysis results 3. Repair suggestions 4. List of root causes of the fault 5. Domain-specific information`

##### 1.8.3.3.2 Variables
1. Variable syntax requirements: When using "variables", use double curly braces "{{}}" for marking, and place the variable name inside the double curly braces, so that the agent can recognize it and thereby implement content input or corresponding reasoning and execution.
2. Variable instantiation methods: Variables need to be correctly instantiated to provide value. Two recommended methods
	- Natural language subject-verb-object structure: The fault occurred at 2026-01-08 16:38:18
	- key-value concise format: 故障发生时间：2026/1/8/16:38:18
3. Common variable set:
A2A-T has summarized commonly used variables in AN L4 high-value scenarios based on best practices. These variables help effectively describe tasks in AN L4 high-value scenarios:
	| Variable Name | Required/Optional | Description & Example |
	|--|--|--|
	| 标识符 |	Required 	| Used to specify the target identifier associated with the task.<br> Example:<br>`## 目标对象`<br> `{{标识符}}` <br>Instance example: <br>`## 目标对象`<br>`Fault identifier (fault-csn) is "OSS-FAULT-20250405-001".`
	|受影响对象|Optional  |Used to specify the network resource object affected by the fault.<br>Example:<br>`## 任务上下文`<br>`{{受影响对象}}`<br>Instance example:<br>`## 任务上下文 `<br>`The ID of the affected object is "BTS-001", type is "Base Station Transceiver", name is "Base Station 001", location is "Chaoyang District, Beijing".`
	|相关信息|Optional| Its general ontology can be a list of events or alarms related to the fault.<br> Example:<br>`## 任务上下文 `<br>`{{相关信息}} `<br>Instance example:<br>`## 任务上下文`<br>`The associated alarm list is as follows: - Alarm identifier (alarm-csn) is "ALM-20250405-001", alarm ID (alarm-id) is "ALM-001", alarm name is "Base Station Signal Loss", network element name is "BTS-001", alarm location is "Chaoyang District, Beijing", alarm occurrence time (alarm-create-time) is "2025-04-05T14:28:00Z". - Alarm identifier (alarm-csn) is "ALM-20250405-002", alarm ID (alarm-id) is "ALM-002", alarm name is "Transmission Link Interruption", network element name is "TRX-002", alarm location is "Haidian District, Beijing", alarm occurrence time (alarm-create-time) is "2025-04-05T14:29:00Z".`
	|故障发生时间| Required | Its general ontology is the time when the fault occurred.<br>Example:<br>`## 任务上下文 `<br>`{{相关信息}}`<br>Instance example:<br>`## 任务上下文 `<br>`Fault occurrence time is "2025-04-05T14:30:00Z".`
	| 故障上下文对象 | Required  | Its general ontology can be fault pre-processing information from OSS, or alarm reporting information from EMS.<br>Example:<br>`## 任务上下文`<br>`{{故障上下文对象}}`<br>Instance example:<br>`## 任务上下文 `<br>`Fault context object is: "Alarm Management System: FMC, alarm location: Beijing, alarm name: Base Station Signal Loss, alarm time: 2025-04-05T14:28:00Z, alarm network element: BTS-001".`
    
#### 1.8.3.4 Format and Specification
A2A-T commonly used text format syntax specification requirements, including paragraphs, lists, links, using Markdown format to ensure structured and readable output:
- Paragraphs: Separate text blocks with blank lines
- Ordered lists: Number plus period (1. Item one)
- Unordered lists: Dash at the beginning (- Item one)
- Links: Square brackets plus parentheses ([text](link))

##### 1.8.3.4.1 Paragraphs
To create a paragraph, use blank lines to separate one or more lines of text. Example:
```
## 任务描述
Handle 5G service fault in Community A

Complete service restoration

Identify the root cause of the fault and perform repair
```

##### 1.8.3.4.2 Ordered Lists
To create an ordered list, add items represented by numbers plus periods. The numbers do not need to be in sequence, but the list should start with number 1. Example:
```
## 预期输出 
1. Bar
2. Foo
```

##### 1.8.3.4.3 Unordered Lists
To create an unordered list, add a dash (-) before each item. Indent one or more items to create a nested list. Example:
```
## 预期输出
- Item
  - Item 1
- Bar2
- Foo
```
##### 1.8.3.4.4 Links
To create a link, enter the link text in square brackets, followed immediately by the URL in parentheses. Example:
```
## 任务描述
Handle the issue of [TM Forum AN](Autonomous Network project homepage) failing to load.
```


#### 1.8.3.5 Steps for Template Definition
It is recommended to define prompt templates following these steps:
1.	Determine task type: Clarify the task type and collaboration mode for the current business scenario, and locate the corresponding template category from the A2A-T task classification system (e.g., Task-T);
2.	Write required instructions: Select key instructions, and use structured format to declare task goals, execution conditions, input parameters, expected output, etc.;
3.	Fill in commonly used variables: Declare the specific parameters involved in this task instance; variable references must follow A2A-T variable syntax specifications;
4.	Bind context information: Supplement the context information needed by the agent to complete the task;
5.	Set output definition: Clearly define the output format, acceptance criteria, and exception handling rules;
6.	Verify template completeness: Conduct thorough testing in actual cross-model environments to verify syntax compliance and cross-LLM compatibility;
7.	Version iteration and optimization: Incorporate validated templates into version management for continuous governance, iteration, and evolution.

## 1.9 Testing Recommendations

Common test commands:

```bash
mvn test
```

When modifying client prompt generation:

```bash
mvn -pl a2a-t-client -am test
```

When modifying server validation:

```bash
mvn -pl a2a-t-server -am test
```

When modifying negotiation state machine:

```bash
mvn -pl a2a-t-negotiation -am test
```

When modifying sample:

```bash
mvn -pl a2a-t-sample -am -DskipTests package
```

## 1.10 Negotiation-T Content Layer

This section describes the Negotiation-T content layer: a template-driven API set for generating negotiation messages, checking their compliance, and extracting parameters from them. Both `A2ATClient` and `A2ATServer` expose the same thirteen methods for this purpose.

### 1.10.1 Overview and SDK Boundary

The content layer covers three capabilities:

1. **Message generation** — renders a structured negotiation message from typed data (`generateNegotiation*PromptFromData`, deterministic, no LLM) or from free text (`generateNegotiation*PromptFromText`, one LLM content-extraction step followed by deterministic rendering).
2. **Compliance checking and parameter extraction** — `validateAndFilling*Data` checks that a received message is a well-formed negotiation message and extracts its parameters per a caller-provided JSON schema.
3. **Template queries** — `getPrompts` / `getPrompt` list and load the templates available for the configured language across **all** A2A-T extensions (Task-T, Notification-T, Authorization-T, Negotiation-T); `getNegotiationPrompts` / `getNegotiationPrompt` restrict the same queries to the negotiation templates.

The content layer is stateless. It deliberately does not own a session state machine: session identity, round tracking beyond what the message itself carries, and role binding stay with the caller. The pre-existing `startNegotiation` / `receiveNegotiation` / `continueNegotiation` API (see 1.6.3) is unchanged and remains the stateful entry point; the content layer can be combined with it or used standalone.

### 1.10.2 Facade Methods

All thirteen methods exist on both `A2ATClient` and `A2ATServer` with identical signatures and semantics.

Every method that identifies a template takes the value type `net.openan.a2at.sdk.core.model.TemplateUri` instead of a raw string. A `TemplateUri` is always well-formed — its constructor validates the extension name, path segments, and version — so malformed URIs cannot reach these APIs. Build one with `TemplateUri.of("Negotiation-T", "information-negotiation", "propose")`, or better, use the constants in `StandardTemplates` from the same package (for example `StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE`, whose `uri()` is `Negotiation-T/information-negotiation/propose/v1`). When a URI string arrives from outside the code, `TemplateUri.parse(String)` returns an `Optional<TemplateUri>` and never throws.

**Generation from typed data — deterministic, never calls an LLM:**

```java
MetadataContent generateNegotiationProposePromptFromData(NegotiationProposeData data, TemplateUri templateUri)
MetadataContent generateNegotiationAcceptPromptFromData(NegotiationEndingData data, TemplateUri templateUri)
MetadataContent generateNegotiationRejectPromptFromData(NegotiationEndingData data, TemplateUri templateUri)
MetadataContent generateNegotiationAbortPromptFromData(NegotiationAbortData data, TemplateUri templateUri)
```

The typed content is validated, dispatched to the generator of the negotiation type addressed by the template URI, and rendered from that template. The accept variant requires `content.conclusion() == ACCEPT`, the reject variant `REJECT`; a mismatched conclusion (including `ABORT`) is rejected with `IllegalArgumentException` as a programming error. The abort variant is type-independent: it renders the single common abort template `StandardTemplates.NEGOTIATION_ABORT` from a `NegotiationAbortData(context, NegotiationAbortContent(terminationReason))` bundle, and the fixed `Abort` conclusion is carried by the template itself.

**Generation from free text — one LLM extraction step with configurable retry:**

```java
MetadataContent generateNegotiationProposePromptFromText(String text, NegotiationContext context, TemplateUri templateUri)
MetadataContent generateNegotiationAcceptPromptFromText(String text, NegotiationContext context, TemplateUri templateUri)
MetadataContent generateNegotiationRejectPromptFromText(String text, NegotiationContext context, TemplateUri templateUri)
MetadataContent generateNegotiationAbortPromptFromText(String text, NegotiationContext context, TemplateUri templateUri)
```

The template is loaded before the LLM call; a missing template fails fast without consuming an LLM request. The LLM step extracts the typed content from the free text constrained by the template URI, then rendering proceeds deterministically like the from-data variant. The `NegotiationContext` is injected into the rendered message without any LLM involvement. The extraction step is retried up to the configured attempt limit on the retryable failure codes (see 1.10.5). The abort variant extracts the termination reason from the free text against the common abort template.

**Compliance checking and parameter extraction:**

```java
FilledParamData validateAndFillingProposeData(String prompt, Map<String, Object> schema, TemplateUri templateUri)
FilledParamData validateAndFillingAcceptData(String prompt, Map<String, Object> schema, TemplateUri templateUri)
FilledParamData validateAndFillingRejectData(String prompt, Map<String, Object> schema, TemplateUri templateUri)
FilledParamData validateAndFillingAbortData(String prompt, Map<String, Object> schema, TemplateUri templateUri)
```

The pipeline runs in a fixed order:

1. Template URI format check (phase segment must match the method: `propose` vs `accept-reject`).
2. Deterministic rule gate — recognition of the negotiation context section and its structural rules, before any LLM call.
3. One LLM semantic validation call that also extracts the parameters, retried up to the configured attempt limit on the retryable LLM infrastructure failure code.
4. Merge of the extracted parameters with the rule-level context parameters — **context parameters take precedence** on key conflict.

**Template queries — never throw:**

```java
List<PromptTemplate> getPrompts()   // templates of ALL extensions for the configured language, sorted by URI
Optional<PromptTemplate> getPrompt(TemplateUri templateUri)  // one template by URI across all extensions; empty on miss

List<PromptTemplate> getNegotiationPrompts()   // negotiation templates of the configured language, fixed order
Optional<PromptTemplate> getNegotiationPrompt(TemplateUri templateUri)  // one negotiation template by URI; empty on miss
```

A missing template yields an empty result with a warning log; because the argument is a validated `TemplateUri`, an unusable URI cannot occur, and a null argument raises `NullPointerException`. `PromptTemplate` is a record `(templateUri, description, content)` whose first component is the typed `TemplateUri` value; `content` is null only when a template record is constructed without available content. The generic pair discovers the extension directories from the bundled resource tree itself — a template directory added under `prompt_resources/templates/` (for example a future `Authorization-T/`) is listed automatically without code changes. Local templates under the configured local resource root override built-in templates of the same path. Note that the SDK does not yet bundle Authorization-T template resources; `generateAuthPromptFromText` / `generateAuthPromptFromDataWithSchema` answer `template_not_found` under the classpath source until such templates are added or provided through the local resource root.

### 1.10.3 Template URIs and Custom Templates

The template URI format is:

```text
Negotiation-T/{type}-negotiation/{phase}/v1
```

where `{type}` is `information`, `target`, or `feasibility`, `{phase}` is `propose` or `accept-reject`, and the trailing `v1` is the template version (the default version). The facade APIs take this as the typed `TemplateUri` value; one built-in constant exists per template in `StandardTemplates`. String-level parsing never throws: `TemplateUri.parse(String)` returns an `Optional<TemplateUri>`, and `NegotiationReference.tryParse` returns an `Optional<NegotiationReference>` — a null, blank or malformed URI (wrong segment count, prefix, version, type or phase segment) simply yields an empty result. Six built-in templates ship with the SDK, one per combination, each in `zh-CN` and `en-US`:

| Template URI | Purpose |
|--|--|
| `Negotiation-T/information-negotiation/propose/v1` | Request missing information |
| `Negotiation-T/information-negotiation/accept-reject/v1` | Accept or reject an information negotiation |
| `Negotiation-T/target-negotiation/propose/v1` | Propose a target negotiation |
| `Negotiation-T/target-negotiation/accept-reject/v1` | Accept or reject a target negotiation |
| `Negotiation-T/feasibility-negotiation/propose/v1` | Request a feasibility evaluation or propose an alternative |
| `Negotiation-T/feasibility-negotiation/accept-reject/v1` | Accept or reject a feasibility negotiation |

Negotiation templates are resolved with a **dual-root fallback** that is independent of `A2AT_PROMPT_SOURCE_TYPE`: a template file that exists under the local resource root configured by `A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR` wins, otherwise the built-in classpath template of the same URI is used. The built-in templates therefore always remain available as a safety net. Templates and the negotiation vocabulary are taken from the local root; LLM prompt resources always come from the classpath. The negotiation vocabulary follows the same dual-root regime: each language's section titles, slot marker names, appended-line labels and list punctuation live in `prompt_resources/negotiation-vocabulary/{lang}/vocabulary.json`, so a file placed at `{A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR}/negotiation-vocabulary/{lang}/vocabulary.json` overrides the built-in classpath vocabulary. A vocabulary file must define exactly the canonical key set shared by both bundled languages; anything else fails fast.

To override one template (for example the information propose template in Chinese), place a file under the local root following the bundled layout:

```text
{A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR}/
  templates/Negotiation-T/information-negotiation/propose/v1/zh-CN/template.md
```

### 1.10.4 Data Models

All types live in `net.openan.a2at.sdk.negotiation.content`.

**NegotiationContext** — the session context carried by every message: `record NegotiationContext(String id, int round, int maxRounds)`. It is immutable; `nextRound()` returns a context with the round incremented, and `isExhausted()` reports whether the round is strictly greater than `maxRounds`. `NegotiationContext.of(id, round)` applies the default round budget of `DEFAULT_MAX_ROUNDS = 5`.

**Input bundles** — `NegotiationProposeData(context, content)` and `NegotiationEndingData(context, content)` pair the context with the typed content, and `NegotiationAbortData(context, NegotiationAbortContent)` pairs it with the type-independent abort content. The content types are sealed hierarchies per negotiation type:

| Negotiation type | Propose content | Ending content |
|--|--|--|
| Information | `InformationProposeContent(List<NegotiationItem> items, String relationship)` | `InformationEndingContent(conclusion, items)` |
| Target | `TargetProposeContent(description, intentUnderstanding, alignmentAndClarification, requestForClarification)` | `TargetEndingContent(conclusion, confirmedIntent, failureReason)` |
| Feasibility | `FeasibilityProposeContent(description, action, contentsToEvaluate, infeasibilityDetailsAndProposal)` | `FeasibilityEndingContent(conclusion, feasibilitySummary)` |

`NegotiationItem(name, value)` is one named entry of an item list. `NegotiationConclusion` carries `ACCEPT`, `REJECT`, and `ABORT`; only `Accept` and `Reject` are renderable conclusions of the typed negotiation templates — the typed generation methods reject `ABORT` with an `IllegalArgumentException` (a programming error outside the `A2ATError` tree). The `ABORT` outcome is rendered through the type-independent common abort template: `NegotiationAbortContent(terminationReason)` is the only content it carries and `NegotiationAbortData(context, content)` is its input bundle. `NegotiationAction` (`REQUEST_FEASIBILITY_EVALUATION`, `PROPOSE_ALTERNATIVE_ON_FAILURE`) selects the conditional sections of the feasibility propose template.

**MetadataContent** — the generation result: `record MetadataContent(String templateUri, String promptText, String extensionUri)`. `buildMetadataContent()` returns exactly two keys: the TMF extension URI (`https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1`) mapping to the rendered message, and `templateUri` mapping to the template URI. This map is what travels in the A2A message metadata.

**FilledParamData** — the extraction result: `record FilledParamData(Map<String, Object> data)` holding the context parameters merged with the schema-extracted parameters.

### 1.10.5 Error Handling

All SDK processing failures share one root, while programming errors stay outside it:

- `A2ATError` — the single root of **all** SDK processing failures (runtime, environment and data-processing failures). The machine-readable code is declared on the root itself: `getCode()` never returns `null` and carries the default `sdk_internal_error` when a failure has no more specific code. Catching this one type therefore covers every processing failure the SDK can raise, LLM and compliance failures included.
  - `NegotiationProcessingException` — a negotiation processing failure.
    - `NegotiationGenerationException` — raised by generation calls.
  - `A2ATParamExtractionError` — a parameter-extraction failure with structured per-slot errors; `getCode()` is inherited from the root, `getErrors()` returns the slot details.
    - `NegotiationParamExtractionException` — raised by `validateAndFilling*Data` calls.
  - `LLMError` — the LLM integration failure subtree, folded into the `A2ATError` tree.
    - `LLMConfigError` — invalid LLM configuration or provider registration.
    - `LLMRuntimeError` — an LLM infrastructure failure at call time.
  - `PromptComplianceCheckException` — a compliance-check failure carrying the code and the compliance stage (`getStage()`); raised by the server-side compliance pipeline with the codes `processed_prompt_parse_error`, `slot_validation_error` and `template_not_found`.
  - The remaining processing failures — prompt generation (`PromptGenerationException`), content validation (`ContentValidationException`), template rendering (`TaskPromptRenderException`), resource resolution (`ResourceNotFoundException`, `ConfigFileNotFoundException`), scenario recognition (`ScenarioRecognitionException`) and negotiation state access (`NegotiationStateException`) — are part of the same tree.

Programming errors — argument-contract violations by the caller — intentionally stay **outside** the `A2ATError` tree, as standard JDK exceptions, so a generic `catch (A2ATError)` handler cannot swallow them: a null argument raises `NullPointerException`, and a blank or semantically contradictory argument (blank context id, invalid segments passed to the `TemplateUri` constructor, phase or conclusion mismatch, unsupported language) raises `IllegalArgumentException`. The former `NegotiationContentException` has been removed; the violations it used to report now surface as these standard exceptions, and each facade method documents them with `@throws` tags.

Error codes and their meaning:

| Code | Meaning | Retryable |
|--|--|--|
| `template_not_found` | No template (or prompt resource) exists for the URI in any resource root | No |
| `negotiation_content_extract_failed` | Structured content could not be extracted from free text | Yes |
| `negotiation_llm_infrastructure_error` | An LLM infrastructure failure (network, provider error, malformed response) | Yes |
| `negotiation_semantic_rejected` | Semantic validation rejected the message | No |
| `negotiation_rule_violation` | The negotiation context section violates a structural rule | No |
| `negotiation_slot_missing` | A required slot is missing when rendering | No |
| `negotiation_invalid_input` | The input is not valid for the operation (blank free text on the from-text generation calls, not a negotiation message, wrong conclusion) | No |
| `param_extraction_failed` | Default code for extraction failures without a more specific code | No |
| `sdk_internal_error` | Default code for SDK processing failures without a more specific code | No |

Retry semantics: a failing LLM step carrying a retryable code is re-run up to the attempt limit configured by `A2AT_LLM_MAX_ATTEMPTS`; failures carrying any other code are rethrown immediately. When the attempts are exhausted, the original failure is rethrown with its original code. `A2AT_LLM_MAX_ATTEMPTS` defaults to 3 and is clamped to the range 1–10 (out-of-range values are clamped with a warning log).

### 1.10.6 Language Configuration

The negotiation templates and the vocabulary used for rendering and section recognition are keyed by `A2AT_LANGUAGE`. Out of the box — built-in classpath resources only — the supported values are exactly `zh-CN` and `en-US`; there is no fallback to another language, any other value without a local vocabulary file fails with an `IllegalArgumentException` when the vocabulary is resolved, and the failure message points at `A2AT_LANGUAGE`. A language for which a valid `vocabulary.json` exists under the local resource root resolves at the vocabulary layer, but generation still fails later with template-not-found unless the full template and prompt resource set for that language is provided as well.

The vocabulary itself is file-driven: each language's constants live in `prompt_resources/negotiation-vocabulary/{lang}/vocabulary.json`, a flat JSON object mapping the 33 canonical keys (section titles, slot marker names, appended-line labels, list punctuation) to that language's text. It follows the same dual-root local-override regime as the templates — a file under `A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR` wins, otherwise the built-in classpath file is used — and resolution is fail-fast with no silent degradation: a vocabulary that exists in neither root, is unreadable, is malformed, or does not define exactly the canonical key set raises an `IllegalArgumentException` naming the language and the file origin. Adding a new language therefore still requires the full resource set — the negotiation templates and the LLM prompt resources — not just a vocabulary file. Set the language explicitly in `client.env` / `server.env`:

```properties
A2AT_LANGUAGE=zh-CN
```

### 1.10.7 End-to-End Example

An information negotiation between a client that proposes (asking for missing data) and a server that accepts and returns the data, using the content layer on both sides:

```java
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.exception.A2ATParamExtractionError;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.negotiation.content.*;
import net.openan.a2at.sdk.server.A2ATServer;

// --- Client side: propose, asking for two missing fields ---
A2ATClient client = new A2ATClient(Path.of("client.env"));

NegotiationContext context = NegotiationContext.of("neg-0001-uuid", 1);
InformationProposeContent content = new InformationProposeContent(
        List.of(
                new NegotiationItem("subscription_condition.incident_level", "critical or warning"),
                new NegotiationItem("notification_data.format", "DataPart or raw JSON")),
        null);

MetadataContent propose = client.generateNegotiationProposePromptFromData(
        new NegotiationProposeData(context, content),
        StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE);

// This two-key map travels in the A2A message metadata.
Map<String, String> metadata = propose.buildMetadataContent();
// metadata.get("templateUri")  -> "Negotiation-T/information-negotiation/propose/v1"
// metadata.get(propose.extensionUri()) -> the rendered message text

// --- Server side: validate the received message and extract parameters ---
A2ATServer server = new A2ATServer(Path.of("server.env"));

Map<String, Object> schema = Map.of(
        "type", "object",
        "properties", Map.of(
                "subscription_condition.incident_level", Map.of("type", "string"),
                "notification_data.format", Map.of("type", "string")),
        "required", List.of("subscription_condition.incident_level"));

try {
    FilledParamData params = server.validateAndFillingProposeData(
            propose.promptText(), schema, StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE);
    // params.data() holds the extracted parameters plus the context parameters.
} catch (A2ATParamExtractionError failure) {
    // Branch on failure.getCode(): negotiation_invalid_input, negotiation_rule_violation,
    // negotiation_semantic_rejected, negotiation_llm_infrastructure_error, template_not_found.
}

// --- Server side: accept and return the requested information ---
InformationEndingContent ending = new InformationEndingContent(
        NegotiationConclusion.ACCEPT,
        List.of(new NegotiationItem("subscription_condition.incident_level", "critical")));

MetadataContent accept = server.generateNegotiationAcceptPromptFromData(
        new NegotiationEndingData(context, ending),
        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);

// The client validates the accept message the same way, with the accept-reject template:
// client.validateAndFillingAcceptData(accept.promptText(), schema,
//         StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);
```

When modifying the negotiation content layer, run:

```bash
mvn -pl a2a-t-negotiation -am test
```
