package net.openan.a2at.sdk.prompt.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.ValidationPipeline;
import net.openan.a2at.sdk.core.validation.ValidationResult;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import net.openan.a2at.sdk.prompt.resources.loader.PromptResourceAccess;
import org.junit.jupiter.api.Test;

class DefaultSemanticValidatorTest {

    @Test
    void validatesEnUSPromptAndExtractsParams() {
        PromptResourceAccess resources =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "classpath", null));

        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "semantic_verdict": true,
                  "errors": [],
                  "params": {"site": "Site A"}
                }
                """);

        DefaultSemanticValidator validator = new DefaultSemanticValidator(llmClient, "en-US", resources);

        ValidationResult result = validator.validate(
                "Check Site A power usage.", Map.of("type", "object"), TemplateUri.of("Task-T", "energy-saving"));

        assertTrue(result.verdict());
        assertEquals(Map.of("site", "Site A"), result.params());

        String expectedSystemPrompt = resources.loadPrompt("content_validation", "en-US", "system.md");
        assertEquals(expectedSystemPrompt, llmClient.lastSystemContent());
    }

    @Test
    void loadsZhCnResourcesWithoutException() {
        PromptResourceAccess resources =
                PromptResourceAccess.create(new PromptRuntimeConfig("zh-CN", "classpath", null));

        DefaultSemanticValidator validator = new DefaultSemanticValidator(null, "zh-CN", resources);

        assertNotNull(validator);
    }

    @Test
    void throwsResourceNotFoundExceptionForMissingLanguage() {
        PromptResourceAccess resources =
                PromptResourceAccess.create(new PromptRuntimeConfig("fr-FR", "classpath", null));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> new DefaultSemanticValidator(null, "fr-FR", resources));

        assertTrue(exception.resourcePath().contains("content_validation"));
    }

    @Test
    void validateRetriesLlmInfrastructureErrorThroughPipeline() {
        PromptResourceAccess resources =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "classpath", null));

        FlakyClient flakyClient = new FlakyClient(
                1,
                """
                {
                  "semantic_verdict": true,
                  "errors": [],
                  "params": {"site": "Site A"}
                }
                """);

        DefaultSemanticValidator validator = new DefaultSemanticValidator(flakyClient, "en-US", resources);

        ValidationPipeline pipeline = new ValidationPipeline(prompt -> Map.of(), validator, 2);

        FilledParamData result = pipeline.validate(
                "Check Site A power usage.", Map.of("type", "object"), TemplateUri.of("Task-T", "energy-saving"));

        assertEquals(Map.of("site", "Site A"), result.data());
        assertEquals(2, flakyClient.invocations());
    }

    @Test
    void validateExhaustsRetriesAndWrapsLlmError() {
        PromptResourceAccess resources =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "classpath", null));

        FlakyClient flakyClient = new FlakyClient(
                Integer.MAX_VALUE,
                """
                {
                  "semantic_verdict": true,
                  "errors": [],
                  "params": {}
                }
                """);

        DefaultSemanticValidator validator = new DefaultSemanticValidator(flakyClient, "en-US", resources);

        ValidationPipeline pipeline = new ValidationPipeline(prompt -> Map.of(), validator, 3);

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> pipeline.validate(
                        "Check Site A power usage.", Map.of("type", "object"), TemplateUri.of("Task-T", "energy-saving")));

        assertEquals(A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR, exception.getCode());
        assertInstanceOf(LLMRuntimeError.class, exception.getCause().getCause());
        assertEquals(3, flakyClient.invocations());
    }

    private static final class RecordingClient implements LLMClient {

        private final String payload;

        private List<Map<String, String>> lastMessages;

        private RecordingClient(String payload) {
            this.payload = payload;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            this.lastMessages = messages;
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }

        String lastSystemContent() {
            for (Map<String, String> message : lastMessages) {
                if ("system".equals(message.get("role"))) {
                    return message.get("content");
                }
            }
            return "";
        }
    }

    private static final class FlakyClient implements LLMClient {

        private final int failuresToSimulate;

        private final String successPayload;

        private final AtomicInteger counter = new AtomicInteger(0);

        private FlakyClient(int failuresToSimulate, String successPayload) {
            this.failuresToSimulate = failuresToSimulate;
            this.successPayload = successPayload;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            int invocation = counter.incrementAndGet();
            if (invocation <= failuresToSimulate) {
                throw new LLMRuntimeError("network timeout");
            }
            return new LLMResponse(
                    successPayload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }

        int invocations() {
            return counter.get();
        }
    }
}
