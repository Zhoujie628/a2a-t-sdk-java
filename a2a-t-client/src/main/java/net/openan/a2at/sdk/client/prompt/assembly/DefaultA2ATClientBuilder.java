package net.openan.a2at.sdk.client.prompt.assembly;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.prompt.extractor.ClientSlotValueExtractor;
import net.openan.a2at.sdk.client.prompt.extractor.DefaultStructuredClientSlotValueExtractor;
import net.openan.a2at.sdk.client.prompt.extractor.DefaultTemplateDrivenSlotValueExtractor;
import net.openan.a2at.sdk.client.prompt.loader.ClientSlotSchemaLoader;
import net.openan.a2at.sdk.client.prompt.loader.ClientTemplateLoader;
import net.openan.a2at.sdk.client.prompt.loader.DefaultClasspathClientSlotSchemaLoader;
import net.openan.a2at.sdk.client.prompt.loader.DefaultClasspathClientTemplateLoader;
import net.openan.a2at.sdk.client.prompt.loader.LocalFileClientSlotSchemaLoader;
import net.openan.a2at.sdk.client.prompt.loader.LocalFileClientTemplateLoader;
import net.openan.a2at.sdk.client.prompt.orchestration.ClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.client.prompt.orchestration.DefaultClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.client.prompt.recognition.ClientScenarioRecognizer;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMConfigLoader;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler;
import net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator;
import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRole;
import net.openan.a2at.sdk.prompt.analysis.impl.ScenarioRecognizer;
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.resources.loader.PromptResourceAccess;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.prompt.taskrendering.api.TaskPromptRenderer;

/**
 * Default builder that assembles one high-level A2AT client runtime from unified config.
 *
 * @since 2026-06
 */
public final class DefaultA2ATClientBuilder {

    private static final String LOCAL_RULE_PROVIDER = "local_rule";

    private static final String OPENAI_PROVIDER = "openai";

    private static final String SCENARIO_RECOGNITION_PROMPT = "scenario_recognition";

    private static final String SLOT_EXTRACTION_PROMPT = "slot_extraction";

    private A2ATConfig config;

    private Path envPath;

    /**
     * Creates one new builder instance.
     *
     * @return empty client builder
     */
    public static DefaultA2ATClientBuilder builder() {
        return new DefaultA2ATClientBuilder();
    }

    /**
     * Configures the unified SDK config consumed by the high-level client facade.
     *
     * @param config unified SDK config
     * @return current builder
     */
    public DefaultA2ATClientBuilder config(A2ATConfig config) {
        this.config = config;
        return this;
    }

    /**
     * Configures the `.env` file path used to assemble downstream facades.
     *
     * @param envPath caller-supplied `.env` path
     * @return current builder
     */
    public DefaultA2ATClientBuilder envPath(Path envPath) {
        this.envPath = envPath;
        return this;
    }

    /**
     * Builds the default prompt-generation orchestrator from the configured unified SDK config.
     *
     * @return assembled prompt-generation orchestrator
     */
    public ClientPromptGenerationOrchestrator buildPromptGenerationOrchestrator() {
        require(config, "Unified SDK config must be configured.");
        require(envPath, "Unified SDK env path must be configured.");
        requireSupportedConfig();

        PromptResourceAccess resources = PromptResourceAccess.create(config.prompt());
        List<ScenarioDefinition> scenarios =
                resources.loadScenarios(config.prompt().language());

        ClientSlotSchemaLoader slotSchemaLoader =
                newClientSlotSchemaLoader(resources, config.prompt().sourceType());
        ClientTemplateLoader templateLoader =
                newClientTemplateLoader(resources, config.prompt().sourceType());
        String language = config.prompt().language();

        boolean isLocalRule = LOCAL_RULE_PROVIDER.equals(config.llm().provider());
        LLMClient llmClient = isLocalRule ? null : createLlmClient();

        String scenarioSystemPrompt = isLocalRule
                ? "" : resources.loadPrompt(SCENARIO_RECOGNITION_PROMPT, language, "system.md");
        String scenarioUserPrompt = isLocalRule
                ? "" : resources.loadPrompt(SCENARIO_RECOGNITION_PROMPT, language, "user.md");
        String slotSystemPrompt = isLocalRule
                ? "" : resources.loadPrompt(SLOT_EXTRACTION_PROMPT, language, "system.md");
        String slotUserPrompt = isLocalRule
                ? "" : resources.loadPrompt(SLOT_EXTRACTION_PROMPT, language, "user.md");

        ClientScenarioRecognizer scenarioRecognizer;
        ClientSlotValueExtractor slotValueExtractor;

        if (isLocalRule) {
            scenarioRecognizer = (normalizedInput, ignoredScenarios, systemPrompt, userPrompt) -> {
                if (scenarios.isEmpty()) {
                    return new ScenarioRecognitionResult(false, null, "No scenarios configured.");
                }
                return new ScenarioRecognitionResult(true, scenarios.get(0).scenarioCode(), null);
            };
            slotValueExtractor = new DefaultTemplateDrivenSlotValueExtractor(slotSchemaLoader);
        } else {
            scenarioRecognizer =
                    new SingleScenarioAwareRecognizer(scenarios, new ScenarioRecognizer(llmClient)::recognize);
            slotValueExtractor = new StructuredInputAwareSlotValueExtractor(
                    new DefaultTemplateDrivenSlotValueExtractor(slotSchemaLoader),
                    new DefaultStructuredClientSlotValueExtractor(
                            llmClient, slotSchemaLoader, slotSystemPrompt, slotUserPrompt));
        }

        return ClientPromptGenerationOrchestratorBuilder.builder()
                .llmClient(llmClient)
                .scenarios(scenarios)
                .language(language)
                .scenarioSystemPrompt(scenarioSystemPrompt)
                .scenarioUserPrompt(scenarioUserPrompt)
                .slotSystemPrompt(slotSystemPrompt)
                .slotUserPrompt(slotUserPrompt)
                .scenarioRecognizer(scenarioRecognizer)
                .templateLoader(templateLoader)
                .slotSchemaLoader(slotSchemaLoader)
                .slotValueExtractor(slotValueExtractor)
                .renderer(new TaskPromptRenderer())
                .build();
    }

    /**
     * Builds the default negotiation orchestrator from the configured unified SDK config.
     *
     * @return assembled negotiation orchestrator
     */
    public RoleBoundNegotiationOrchestrator buildNegotiationOrchestrator() {
        require(config, "Unified SDK config must be configured.");
        requireSupportedConfig();
        return new RoleBoundNegotiationOrchestrator(
                NegotiationHandler.builder()
                        .store(new InMemoryNegotiationStore())
                        .build(),
                NegotiationRole.CLIENT);
    }

    /**
     * Builds the default negotiation content-layer orchestrator from the configured unified SDK config.
     *
     * <p>The wiring mirrors the server side: the message language and the local template root come from the prompt
     * runtime config, the retry attempt limit comes from the LLM config, and the LLM client is only created when the
     * provider is not {@code local_rule}.
     *
     * @return assembled negotiation generation orchestrator
     */
    public NegotiationGenerationOrchestrator buildNegotiationGenerationOrchestrator() {
        require(config, "Unified SDK config must be configured.");
        requireSupportedConfig();
        LLMClient llmClient = null;
        if (!LOCAL_RULE_PROVIDER.equals(config.llm().provider())) {
            require(envPath, "Unified SDK env path must be configured.");
            llmClient = createLlmClient();
        }
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(config.prompt().language())
                .localRootDir(config.prompt().localRootDir())
                .llmClient(llmClient)
                .maxAttempts(config.llm().maxAttempts())
                .build();
    }

    private void requireSupportedConfig() {
        if (!PromptResourceAccess.CLASSPATH_SOURCE_TYPE.equals(config.prompt().sourceType())
                && !PromptResourceAccess.LOCAL_FILE_SOURCE_TYPE.equals(
                        config.prompt().sourceType())) {
            throw new UnsupportedOperationException(
                    "Unsupported prompt source type: " + config.prompt().sourceType());
        }
        if (!LOCAL_RULE_PROVIDER.equals(config.llm().provider())
                && !OPENAI_PROVIDER.equals(config.llm().provider())) {
            throw new UnsupportedOperationException(
                    "Unsupported LLM provider: " + config.llm().provider());
        }
        if (!"in_memory".equals(config.negotiation().stateStoreType())) {
            throw new UnsupportedOperationException("Unsupported negotiation state store type: "
                    + config.negotiation().stateStoreType());
        }
    }

    private LLMClient createLlmClient() {
        LLMClientConfig loadedConfig = LLMConfigLoader.load(envPath);
        return LLMClientFactory.create(loadedConfig.provider(), loadedConfig);
    }

    private static void require(Object value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
    }

    private static ClientTemplateLoader newClientTemplateLoader(PromptResourceAccess resources, String sourceType) {
        if (PromptResourceAccess.CLASSPATH_SOURCE_TYPE.equals(sourceType)) {
            return new DefaultClasspathClientTemplateLoader(resources.classpathResourceLoader());
        }
        if (PromptResourceAccess.LOCAL_FILE_SOURCE_TYPE.equals(sourceType)) {
            return new LocalFileClientTemplateLoader(resources.localRootDir());
        }
        throw new UnsupportedOperationException("Unsupported prompt source type: " + sourceType);
    }

    private static ClientSlotSchemaLoader newClientSlotSchemaLoader(PromptResourceAccess resources, String sourceType) {
        if (PromptResourceAccess.CLASSPATH_SOURCE_TYPE.equals(sourceType)) {
            return new DefaultClasspathClientSlotSchemaLoader(resources.classpathResourceLoader());
        }
        if (PromptResourceAccess.LOCAL_FILE_SOURCE_TYPE.equals(sourceType)) {
            return new LocalFileClientSlotSchemaLoader(resources.localRootDir());
        }
        throw new UnsupportedOperationException("Unsupported prompt source type: " + sourceType);
    }

    private record SingleScenarioAwareRecognizer(List<ScenarioDefinition> scenarios, ClientScenarioRecognizer delegate)
            implements ClientScenarioRecognizer {

        @Override
        public ScenarioRecognitionResult recognize(
                String normalizedInput,
                List<ScenarioDefinition> ignoredScenarios,
                String systemPrompt,
                String userPrompt) {
            if (scenarios.size() == 1) {
                return new ScenarioRecognitionResult(true, scenarios.get(0).scenarioCode(), null);
            }
            return delegate.recognize(normalizedInput, scenarios, systemPrompt, userPrompt);
        }
    }

    private record StructuredInputAwareSlotValueExtractor(
            ClientSlotValueExtractor structuredExtractor, ClientSlotValueExtractor llmExtractor)
            implements ClientSlotValueExtractor {

        @Override
        public Map<String, String> extractSlots(
                Object userInput, String scenarioCode, String language, String templateText) {
            if (userInput instanceof Map<?, ?>) {
                return structuredExtractor.extractSlots(userInput, scenarioCode, language, templateText);
            }
            return llmExtractor.extractSlots(userInput, scenarioCode, language, templateText);
        }

        @Override
        public Map<String, String> extractSlotsWithSchema(
                Object userInput, String scenarioCode, String language, String templateText,
                Map<String, Object> dataSchema) {
            return llmExtractor.extractSlotsWithSchema(userInput, scenarioCode, language, templateText, dataSchema);
        }
    }
}