package net.openan.a2at.sdk.client;

import java.nio.file.Path;
import java.util.Map;
import net.openan.a2at.sdk.client.model.MetadataContent;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.client.prompt.assembly.DefaultA2ATClientBuilder;
import net.openan.a2at.sdk.client.prompt.orchestration.ClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;

/**
 * High-level client facade for prompt generation and negotiation APIs. The caller provides the `.env` file path
 * explicitly, typically after copying the repository `env.example`.
 *
 * @since 2026-06
 */
public final class A2ATClient {

    private final ClientPromptGenerationOrchestrator promptGenerationOrchestrator;

    private final RoleBoundNegotiationOrchestrator negotiationOrchestrator;

    /**
     * Creates a client facade from one user-supplied `.env` path.
     *
     * @param envPath user-supplied `.env` file path
     */
    public A2ATClient(Path envPath) {
        Path resolvedEnvPath = envPath.toAbsolutePath().normalize();
        A2ATConfig config = resolvePromptResourceLocalRootDir(A2ATConfig.load(resolvedEnvPath), resolvedEnvPath);
        DefaultA2ATClientBuilder builder =
                DefaultA2ATClientBuilder.builder().envPath(resolvedEnvPath).config(config);
        this.promptGenerationOrchestrator = builder.buildPromptGenerationOrchestrator();
        this.negotiationOrchestrator = builder.buildNegotiationOrchestrator();
    }

    /**
     * Generates a processed task prompt from raw user input.
     *
     * @param userInput user-provided task description or structured input map
     * @return prompt generation result containing either rendered prompt text or failure details
     */
    public PromptGenerationResult generateTaskPrompt(Object userInput) {
        return promptGenerationOrchestrator.generateTaskPrompt(userInput);
    }

    /**
     * Generates a processed task prompt from natural-language input using the template identified by the template URI,
     * bypassing scenario recognition.
     *
     * @param taskInputNl natural-language task input
     * @param promptTemplateUri template URI identifying the target template
     * @return prompt generation result containing either rendered prompt text or failure details
     */
    public PromptGenerationResult generateTaskPromptFromNl(String taskInputNl, String promptTemplateUri) {
        return promptGenerationOrchestrator.generateTaskPromptFromNl(taskInputNl, promptTemplateUri);
    }

    /**
     * Generates a processed task prompt from structured input using the template identified by the template URI,
     * bypassing scenario recognition.
     *
     * @param taskInputJsonData structured task input as a string-to-object map
     * @param promptTemplateUri template URI identifying the target template
     * @return prompt generation result containing either rendered prompt text or failure details
     */
    public PromptGenerationResult generateTaskPromptFromJsonData(
            Map<String, Object> taskInputJsonData, String promptTemplateUri) {
        return promptGenerationOrchestrator.generateTaskPromptFromJsonData(taskInputJsonData, promptTemplateUri);
    }

    /**
     * Generates an authorization prompt from natural-language input using the template identified by the authorization
     * type, bypassing scenario recognition.
     *
     * @param inputNl natural-language authorization input
     * @param authorizationType authorization type used as the template identifier
     * @return prompt generation result containing either rendered prompt text or failure details
     */
    public PromptGenerationResult generateAuthorizationPromptFromNl(String inputNl, String authorizationType) {
        return promptGenerationOrchestrator.generateAuthorizationPromptFromNl(inputNl, authorizationType);
    }

    /**
     * Generates an authorization prompt from structured input using the template identified by the authorization type,
     * bypassing scenario recognition.
     *
     * @param inputJsonData structured authorization input as a string-to-object map
     * @param authorizationType authorization type used as the template identifier
     * @return prompt generation result containing either rendered prompt text or failure details
     */
    public PromptGenerationResult generateAuthorizationPromptFromJsonData(
            Map<String, Object> inputJsonData, String authorizationType) {
        return promptGenerationOrchestrator.generateAuthorizationPromptFromJsonData(inputJsonData, authorizationType);
    }

    /**
     * Generates a notification prompt from natural-language input using the template identified by the template URI,
     * bypassing scenario recognition.
     *
     * @param inputNl natural-language notification input
     * @param promptTemplateUri template URI identifying the target template
     * @return prompt generation result containing either rendered prompt text or failure details
     */
    public PromptGenerationResult generateNotificationPromptFromNl(String inputNl, String promptTemplateUri) {
        return promptGenerationOrchestrator.generateNotificationPromptFromNl(inputNl, promptTemplateUri);
    }

    /**
     * Generates a notification prompt from structured input using the template identified by the template URI,
     * bypassing scenario recognition.
     *
     * @param inputJsonData structured notification input as a string-to-object map
     * @param promptTemplateUri template URI identifying the target template
     * @return prompt generation result containing either rendered prompt text or failure details
     */
    public PromptGenerationResult generateNotificationPromptFromJsonData(
            Map<String, Object> inputJsonData, String promptTemplateUri) {
        return promptGenerationOrchestrator.generateNotificationPromptFromJsonData(inputJsonData, promptTemplateUri);
    }

    /**
     * Generates a task prompt with metadata from natural-language input using the template identified by the template
     * URI, bypassing scenario recognition.
     *
     * @param text natural-language task input
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    public MetadataContent generateTaskPromptFromText(String text, String templateUri) {
        return promptGenerationOrchestrator.generateTaskPromptFromText(text, templateUri);
    }

    /**
     * Generates a task prompt with metadata from structured input and an optional data schema using the template
     * identified by the template URI, bypassing scenario recognition.
     *
     * @param data structured task input as a string-to-object map
     * @param schema optional data schema map for schema-guided extraction
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    public MetadataContent generateTaskPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, String templateUri) {
        return promptGenerationOrchestrator.generateTaskPromptFromDataWithSchema(data, schema, templateUri);
    }

    /**
     * Generates an authorization prompt with metadata from natural-language input using the template identified by the
     * authorization type, bypassing scenario recognition.
     *
     * @param text natural-language authorization input
     * @param authorizationType authorization type used as the template identifier
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    public MetadataContent generateAuthPromptFromText(String text, String authorizationType) {
        return promptGenerationOrchestrator.generateAuthPromptFromText(text, authorizationType);
    }

    /**
     * Generates an authorization prompt with metadata from structured input and an optional data schema using the
     * template identified by the authorization type, bypassing scenario recognition.
     *
     * @param data structured authorization input as a string-to-object map
     * @param schema optional data schema map for schema-guided extraction
     * @param authorizationType authorization type used as the template identifier
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    public MetadataContent generateAuthPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, String authorizationType) {
        return promptGenerationOrchestrator.generateAuthPromptFromDataWithSchema(data, schema, authorizationType);
    }

    /**
     * Generates a notification prompt with metadata from natural-language input using the template identified by the
     * template URI, bypassing scenario recognition.
     *
     * @param text natural-language notification input
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    public MetadataContent generateNotificationPromptFromText(String text, String templateUri) {
        return promptGenerationOrchestrator.generateNotificationPromptFromText(text, templateUri);
    }

    /**
     * Generates a notification prompt with metadata from structured input and an optional data schema using the
     * template identified by the template URI, bypassing scenario recognition.
     *
     * @param data structured notification input as a string-to-object map
     * @param schema optional data schema map for schema-guided extraction
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    public MetadataContent generateNotificationPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, String templateUri) {
        return promptGenerationOrchestrator.generateNotificationPromptFromDataWithSchema(data, schema, templateUri);
    }

    /**
     * Starts a new negotiation payload for the requested negotiation type.
     *
     * @param type negotiation type to initiate
     * @param contentText human-readable negotiation message
     * @param facts structured facts attached to the payload
     * @return transport payload representing the initial negotiation turn
     */
    public Map<String, Object> startNegotiation(NegotiationType type, String contentText, Map<String, Object> facts) {
        return negotiationOrchestrator.startNegotiation(type, contentText, facts);
    }

    /**
     * Processes a received negotiation message using its transport context payload.
     *
     * @param message received negotiation message
     * @param context transport context payload associated with the message
     * @return normalized payload describing the receive result
     */
    public Map<String, Object> receiveNegotiation(String message, Map<String, Object> context) {
        return negotiationOrchestrator.receiveNegotiation(message, context);
    }

    /**
     * Continues an existing negotiation with a locally stored context snapshot.
     *
     * @param context current negotiation context
     * @param status next status to emit
     * @param contentText continuation message content
     * @return transport payload representing the next negotiation turn
     */
    public Map<String, Object> continueNegotiation(
            NegotiationContext context, NegotiationStatus status, String contentText) {
        return negotiationOrchestrator.continueNegotiation(context, status, contentText);
    }

    private A2ATConfig resolvePromptResourceLocalRootDir(A2ATConfig config, Path envPath) {
        String localRootDir = config.prompt().localRootDir();
        Path localRootPath = Path.of(localRootDir);
        Path resolvedLocalRootPath = localRootPath.isAbsolute()
                ? localRootPath.normalize()
                : envPath.getParent().resolve(localRootPath).toAbsolutePath().normalize();
        return new A2ATConfig(
                new PromptRuntimeConfig(
                        config.prompt().language(), config.prompt().sourceType(), resolvedLocalRootPath.toString()),
                config.llm(),
                config.negotiation(),
                config.promptCompliance());
    }
}
