package net.openan.a2at.sdk.server.assembly;

import java.nio.file.Path;
import java.util.List;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMConfigLoader;
import net.openan.a2at.sdk.negotiation.content.NegotiationContentService;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator;
import net.openan.a2at.sdk.prompt.resources.catalog.TemplateQueryService;
import net.openan.a2at.sdk.prompt.analysis.impl.DefaultStructuredPromptSlotValueExtractor;
import net.openan.a2at.sdk.prompt.analysis.impl.ScenarioRecognizer;
import net.openan.a2at.sdk.prompt.resources.loader.PromptResourceAccess;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.server.compliance.DefaultServerPromptComplianceOrchestrator;
import net.openan.a2at.sdk.server.metadata.LlmBackedPromptMetadataExtractor;
import net.openan.a2at.sdk.server.validation.LlmBackedPromptSemanticValidator;

/**
 * Default builder that assembles one high-level A2AT server runtime from unified config.
 *
 * @since 2026-06
 */
public final class DefaultA2ATServerBuilder {

    private static final String SCENARIO_RECOGNITION_PROMPT = "scenario_recognition";

    private static final String SLOT_EXTRACTION_PROMPT = "slot_extraction";

    private static final String SEMANTIC_VALIDATION_PROMPT = "semantic_validation";

    private A2ATConfig config;

    private Path envPath;

    /**
     * Creates one new builder instance.
     *
     * @return empty server builder
     */
    public static DefaultA2ATServerBuilder builder() {
        return new DefaultA2ATServerBuilder();
    }

    /**
     * Configures the unified SDK config consumed by the high-level server facade.
     *
     * @param config unified SDK config
     * @return current builder
     */
    public DefaultA2ATServerBuilder config(A2ATConfig config) {
        this.config = config;
        return this;
    }

    /**
     * Configures the `.env` file path used to assemble downstream facades.
     *
     * @param envPath caller-supplied `.env` path
     * @return current builder
     */
    public DefaultA2ATServerBuilder envPath(Path envPath) {
        this.envPath = envPath;
        return this;
    }

    /**
     * Builds the default prompt-compliance orchestrator from the configured unified SDK config.
     *
     * @return assembled prompt-compliance orchestrator
     */
    public DefaultServerPromptComplianceOrchestrator buildPromptComplianceOrchestrator() {
        require(config, "Unified SDK config must be configured.");
        require(envPath, "Unified SDK env path must be configured.");
        requireSupportedConfig();

        PromptResourceAccess resources = PromptResourceAccess.create(config.prompt());
        String language = config.prompt().language();
        List<ScenarioDefinition> scenarios = resources.loadScenarios(language);
        PromptTemplateTextLoader templateLoader = resources.templateLoader();
        PromptSlotSchemaLoader slotSchemaLoader = resources.slotSchemaLoader();
        LLMClient llmClient = createLlmClient();

        String scenarioSystemPrompt = resources.loadPrompt(SCENARIO_RECOGNITION_PROMPT, language, "system.md");
        String scenarioUserPrompt = resources.loadPrompt(SCENARIO_RECOGNITION_PROMPT, language, "user.md");
        String slotSystemPrompt = resources.loadPrompt(SLOT_EXTRACTION_PROMPT, language, "system.md");
        String slotUserPrompt = resources.loadPrompt(SLOT_EXTRACTION_PROMPT, language, "user.md");
        String semanticSystemPrompt = resources.loadPrompt(SEMANTIC_VALIDATION_PROMPT, language, "system.md");
        String semanticUserPrompt = resources.loadPrompt(SEMANTIC_VALIDATION_PROMPT, language, "user.md");

        return new DefaultServerPromptComplianceOrchestrator(
                new LlmBackedPromptMetadataExtractor(
                        new ScenarioRecognizer(llmClient),
                        scenarios,
                        language,
                        scenarioSystemPrompt,
                        scenarioUserPrompt,
                        templateLoader,
                        slotSchemaLoader,
                        new DefaultStructuredPromptSlotValueExtractor(
                                llmClient, slotSchemaLoader, slotSystemPrompt, slotUserPrompt)),
                new LlmBackedPromptSemanticValidator(
                        llmClient, slotSchemaLoader, semanticSystemPrompt, semanticUserPrompt));
    }

    /**
     * Builds the default negotiation orchestrator from the configured unified SDK config.
     *
     * @return assembled negotiation orchestrator
     */
    public RoleBoundNegotiationOrchestrator buildNegotiationOrchestrator() {
        require(config, "Unified SDK config must be configured.");
        requireSupportedConfig();
        return new ServerNegotiationOrchestratorBuilder()
                .promptComplianceOrchestrator(buildPromptComplianceOrchestrator())
                .build();
    }

    /**
     * Builds the default negotiation content-layer orchestrator from the configured unified SDK config.
     *
     * <p>The wiring is shared with the client side through
     * {@link NegotiationContentService#buildOrchestrator(A2ATConfig, LLMClient)}: the message language and the local
     * template root come from the prompt runtime config, the retry attempt limit comes from the LLM config.
     *
     * @return assembled negotiation generation orchestrator
     */
    public NegotiationGenerationOrchestrator buildNegotiationGenerationOrchestrator() {
        require(config, "Unified SDK config must be configured.");
        requireSupportedConfig();
        require(envPath, "Unified SDK env path must be configured.");
        return NegotiationContentService.buildOrchestrator(config, createLlmClient());
    }

    /**
     * Builds the generic template query service from the configured unified SDK config.
     *
     * <p>The service answers the extension-agnostic template queries: the message language and the local template
     * root come from the prompt runtime config, exactly like the negotiation generation orchestrator wiring.
     *
     * @return assembled template query service
     */
    public TemplateQueryService buildTemplateQueryService() {
        require(config, "Unified SDK config must be configured.");
        requireSupportedConfig();
        return new TemplateQueryService(config.prompt().language(), config.prompt().localRootDir());
    }

    private static void require(Object value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
    }

    private void requireSupportedConfig() {
        if (!PromptResourceAccess.CLASSPATH_SOURCE_TYPE.equals(config.prompt().sourceType())
                && !PromptResourceAccess.LOCAL_FILE_SOURCE_TYPE.equals(config.prompt().sourceType())) {
            throw new UnsupportedOperationException(
                    "Unsupported prompt source type: " + config.prompt().sourceType());
        }
        if (!LLMClientFactory.availableProviders().contains(config.llm().provider())) {
            throw new UnsupportedOperationException("Unsupported LLM provider: " + config.llm().provider());
        }
        if (!"in_memory".equals(config.negotiation().stateStoreType())) {
            throw new UnsupportedOperationException(
                    "Unsupported negotiation state store type: " + config.negotiation().stateStoreType());
        }
    }

    private LLMClient createLlmClient() {
        LLMClientConfig loadedConfig = LLMConfigLoader.load(envPath);
        return LLMClientFactory.create(loadedConfig.provider(), loadedConfig);
    }

}