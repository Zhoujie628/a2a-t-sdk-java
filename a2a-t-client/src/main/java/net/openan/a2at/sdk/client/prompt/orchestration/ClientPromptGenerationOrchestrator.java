package net.openan.a2at.sdk.client.prompt.orchestration;

import java.util.Map;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.core.validation.TemplateUri;

/**
 * Internal prompt-generation orchestration contract used by the client facade.
 *
 * @since 2026-06
 */
public interface ClientPromptGenerationOrchestrator {

    /**
     * Generates a processed task prompt from user input.
     *
     * @param userInput raw or structured user input
     * @return prompt-generation result
     */
    PromptGenerationResult generateTaskPrompt(Object userInput);

    /**
     * Generates a task prompt with metadata from natural-language input using the template identified by the template
     * URI, bypassing scenario recognition.
     *
     * @param text natural-language task input
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    MetadataContent generateTaskPromptFromText(String text, TemplateUri templateUri);

    /**
     * Generates a task prompt with metadata from structured input and an optional data schema using the template
     * identified by the template URI, bypassing scenario recognition.
     *
     * @param data structured task input as a string-to-object map
     * @param schema optional data schema map for schema-guided extraction
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    MetadataContent generateTaskPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri);

    /**
     * Generates an authorization prompt with metadata from natural-language input using the template identified by the
     * template URI, bypassing scenario recognition.
     *
     * @param text natural-language authorization input
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    MetadataContent generateAuthPromptFromText(String text, TemplateUri templateUri);

    /**
     * Generates an authorization prompt with metadata from structured input and an optional data schema using the
     * template identified by the template URI, bypassing scenario recognition.
     *
     * @param data structured authorization input as a string-to-object map
     * @param schema optional data schema map for schema-guided extraction
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    MetadataContent generateAuthPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri);

    /**
     * Generates a notification prompt with metadata from natural-language input using the template identified by the
     * template URI, bypassing scenario recognition.
     *
     * @param text natural-language notification input
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    MetadataContent generateNotificationPromptFromText(String text, TemplateUri templateUri);

    /**
     * Generates a notification prompt with metadata from structured input and an optional data schema using the
     * template identified by the template URI, bypassing scenario recognition.
     *
     * @param data structured notification input as a string-to-object map
     * @param schema optional data schema map for schema-guided extraction
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    MetadataContent generateNotificationPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri);
}