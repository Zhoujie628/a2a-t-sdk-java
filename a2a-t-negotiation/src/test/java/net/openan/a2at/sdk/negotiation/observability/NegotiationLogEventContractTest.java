package net.openan.a2at.sdk.negotiation.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.FilledParamData;
import net.openan.a2at.sdk.negotiation.content.InfoProposeContent;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.resources.NegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.resources.PromptTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Locks the log instrumentation contract of the negotiation content layer.
 *
 * <p>Every pipeline path is driven through the real default collaborators with a scripted LLM client and the emitted
 * events are captured with a logback {@link ListAppender}. The test asserts that exactly the twelve contract event
 * names appear, each at its configured level, that every message is an English snake_case event followed by
 * {@code key=value} fields, that internal step diagnostics live in the logs only, and that neither the message text,
 * the free-text input nor the raw LLM response ever leaks into an INFO-or-higher event.
 */
class NegotiationLogEventContractTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String INFORMATION_PROPOSE_URI = "Negotiation-T/v1/information-negotiation/propose";

    private static final Map<String, Set<Level>> EXPECTED_EVENT_LEVELS = Map.ofEntries(
            Map.entry("negotiation_template_loaded", Set.of(Level.DEBUG)),
            Map.entry("negotiation_generator_dispatched", Set.of(Level.DEBUG)),
            Map.entry("negotiation_content_extraction_completed", Set.of(Level.INFO)),
            Map.entry("negotiation_semantic_validation_completed", Set.of(Level.INFO)),
            Map.entry("negotiation_param_extraction_completed", Set.of(Level.INFO)),
            Map.entry("negotiation_generation_completed", Set.of(Level.INFO)),
            Map.entry("negotiation_llm_retry", Set.of(Level.WARN)),
            Map.entry("negotiation_llm_retry_exhausted", Set.of(Level.WARN)),
            Map.entry("negotiation_generation_failed", Set.of(Level.WARN)),
            Map.entry("negotiation_param_extraction_failed", Set.of(Level.WARN)),
            Map.entry("negotiation_template_not_found", Set.of(Level.WARN)),
            Map.entry("negotiation_rule_checks_completed", Set.of(Level.DEBUG, Level.WARN)));

    private Logger rootLogger;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void allTwelveEventNamesFireAtTheirConfiguredLevelsAcrossThePipelines() {
        driveSuccessfulGenerationFromData();
        driveSuccessfulGenerationFromText();
        driveRetryThenSuccessOnContentExtraction();
        driveRetryExhaustionOnContentExtraction();
        driveSuccessfulParamExtraction();
        driveSemanticRejection();
        driveRuleViolation();
        driveQueryBoundaries();

        Map<String, Set<Level>> observedLevels = negotiationEventLevels();
        assertEquals(EXPECTED_EVENT_LEVELS.keySet(), observedLevels.keySet());
        EXPECTED_EVENT_LEVELS.forEach((eventName, expectedLevels) ->
                assertEquals(expectedLevels, observedLevels.get(eventName), "levels of event " + eventName));
    }

    @Test
    void everyEventMessageIsAnEnglishSnakeCaseEventWithKeyValueFields() {
        driveSuccessfulGenerationFromData();
        driveSuccessfulGenerationFromText();
        driveSuccessfulParamExtraction();
        driveQueryBoundaries();

        List<ILoggingEvent> events = negotiationEvents();
        assertFalse(events.isEmpty());
        for (ILoggingEvent event : events) {
            String message = event.getFormattedMessage();
            String eventName = message.split(" ")[0];
            assertTrue(
                    eventName.matches("negotiation_[a-z0-9]+(_[a-z0-9]+)*"),
                    "event name must be snake_case but was: " + eventName);
            assertTrue(
                    message.chars().allMatch(character -> character >= 32 && character < 127),
                    "event message must be printable ASCII but was: " + message);
            List<String> keyTokens = keyTokens(message);
            assertFalse(keyTokens.isEmpty(), "event message must carry key=value fields: " + message);
            for (String key : keyTokens) {
                assertTrue(
                        key.matches("[a-z][a-z0-9_]*"),
                        "field key must be snake_case but was: " + key + " in " + message);
            }
        }
    }

    @Test
    void retryEventsCarryTheInternalStepNameThatNeverAppearsOnExceptions() {
        driveRetryExhaustionOnContentExtraction();

        List<String> retryMessages = messagesOf("negotiation_llm_retry");
        List<String> exhaustedMessages = messagesOf("negotiation_llm_retry_exhausted");
        assertEquals(1, retryMessages.size());
        assertEquals(1, exhaustedMessages.size());
        assertTrue(retryMessages.get(0).contains("step="));
        assertTrue(retryMessages.get(0).contains("attempt=1"));
        assertTrue(retryMessages.get(0).contains("max_attempts="));
        assertTrue(retryMessages.get(0).contains("code=" + A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR));
        assertTrue(exhaustedMessages.get(0).contains("step="));

        NegotiationGenerationException failure = generationFailureOfExhaustedExtraction();
        assertNotNull(failure);
        assertFalse(failure.getMessage().toLowerCase().contains("stage"));
        assertFalse(failure.getMessage().contains("step="));
    }

    @Test
    void infoAndHigherEventsNeverCarryMessageTextInputOrResponseContent() {
        String inputMarker = "SECRET-INPUT-MARKER-7f3a";
        String promptMarker = "SECRET-PROMPT-MARKER-9b1c";
        String responseMarker = "SECRET-RESPONSE-MARKER-5d2e";
        String paramValueMarker = "SECRET-PARAM-VALUE-MARKER-3c8d";

        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(
                        "{\"items\":[{\"name\":\"item\",\"value\":\"" + responseMarker + "\"}],\"relationship\":null}",
                        "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                                + "\"params\":{\"region\":\"" + paramValueMarker + "\"}}"))
                .build();

        MetadataContent generated = orchestrator.generateProposeFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5),
                        new InfoProposeContent(List.of(new NegotiationItem(promptMarker, "value")), null)),
                INFORMATION_PROPOSE_URI);
        assertTrue(generated.promptText().contains(promptMarker));
        orchestrator.generateProposeFromText(
                "free text containing " + inputMarker, new NegotiationContext(UUID, 1, 5), INFORMATION_PROPOSE_URI);
        FilledParamData filled = orchestrator.validateAndFillingProposeData(
                generated.promptText(), Map.of("type", "object"), INFORMATION_PROPOSE_URI);
        assertTrue(filled.data().containsValue(paramValueMarker));

        List<ILoggingEvent> infoAndHigher = appender.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.INFO))
                .toList();
        assertFalse(infoAndHigher.isEmpty());
        for (ILoggingEvent event : infoAndHigher) {
            String message = event.getFormattedMessage();
            assertFalse(message.contains(inputMarker), "input text leaked at " + event.getLevel() + ": " + message);
            assertFalse(message.contains(promptMarker), "prompt text leaked at " + event.getLevel() + ": " + message);
            assertFalse(
                    message.contains(responseMarker),
                    "response content leaked at " + event.getLevel() + ": " + message);
            assertFalse(
                    message.contains(paramValueMarker),
                    "extracted parameter value leaked at " + event.getLevel() + ": " + message);
            assertFalse(
                    message.contains("prompt_text=") || message.contains("text=") || message.contains("response="),
                    "raw content field must not be logged at " + event.getLevel() + ": " + message);
        }
    }

    @Test
    void queryBoundariesEmitTemplateNotFoundWarningsWithActionableHints() {
        NegotiationGenerationOrchestrator defaultLoaderOrchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .templateLoader(missingTemplateLoader())
                .build();

        defaultLoaderOrchestrator.getNegotiationPrompts();
        defaultLoaderOrchestrator.getNegotiationPrompt(INFORMATION_PROPOSE_URI);

        NegotiationGenerationOrchestrator builtinOrchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .build();
        builtinOrchestrator.getNegotiationPrompt("malformed-template-uri");

        List<String> warnings = messagesOf("negotiation_template_not_found");
        assertEquals(3, warnings.size());
        assertTrue(warnings.get(0).contains("uri=all"));
        assertTrue(warnings.get(0).contains("language=zh-CN"));
        assertTrue(warnings.get(1).contains("uri=" + INFORMATION_PROPOSE_URI));
        assertTrue(warnings.get(1).contains("language=zh-CN"));
        assertTrue(warnings.get(2).contains("reason=invalid_template_uri"));
        for (String warning : warnings) {
            assertTrue(warning.contains("hint="), "boundary warning must carry a hint: " + warning);
            assertTrue(
                    warning.contains("A2AT_LANGUAGE"), "boundary warning hint must mention A2AT_LANGUAGE: " + warning);
        }
    }

    @Test
    void removedTypeRecognitionEventNeverAppears() {
        driveSuccessfulGenerationFromText();
        driveSuccessfulParamExtraction();

        assertTrue(appender.list.stream()
                .noneMatch(event -> event.getFormattedMessage().startsWith("negotiation_type_recognition")));
    }

    private void driveSuccessfulGenerationFromData() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(validExtractionPayload(), validSemanticPayload()))
                .build();
        MetadataContent result = orchestrator.generateProposeFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5),
                        new InfoProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                INFORMATION_PROPOSE_URI);
        assertTrue(result.promptText().contains("协商上下文"));
    }

    private void driveSuccessfulGenerationFromText() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(validExtractionPayload(), validSemanticPayload()))
                .build();
        MetadataContent result = orchestrator.generateProposeFromText(
                "请提供节能区域。", new NegotiationContext(UUID, 2, 5), INFORMATION_PROPOSE_URI);
        assertTrue(result.promptText().contains("所需信息项"));
    }

    private void driveRetryThenSuccessOnContentExtraction() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient("", validExtractionPayload()))
                .maxAttempts(2)
                .build();
        MetadataContent result = orchestrator.generateProposeFromText(
                "请提供节能区域。", new NegotiationContext(UUID, 1, 5), INFORMATION_PROPOSE_URI);
        assertTrue(result.promptText().contains("所需信息项"));
    }

    private void driveRetryExhaustionOnContentExtraction() {
        NegotiationGenerationException failure = generationFailureOfExhaustedExtraction();
        assertNotNull(failure);
    }

    private NegotiationGenerationException generationFailureOfExhaustedExtraction() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new FailingClient())
                .maxAttempts(2)
                .build();
        try {
            orchestrator.generateProposeFromText(
                    "请提供节能区域。", new NegotiationContext(UUID, 1, 5), INFORMATION_PROPOSE_URI);
        } catch (NegotiationGenerationException failure) {
            assertEquals(A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR, failure.getCode());
            return failure;
        }
        return null;
    }

    private void driveSuccessfulParamExtraction() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(validSemanticPayload()))
                .build();
        MetadataContent message = orchestrator.generateProposeFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5),
                        new InfoProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                INFORMATION_PROPOSE_URI);
        FilledParamData filled = orchestrator.validateAndFillingProposeData(
                message.promptText(), Map.of("type", "object"), INFORMATION_PROPOSE_URI);
        assertEquals(UUID, filled.data().get("id"));
    }

    private void driveSemanticRejection() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(
                        "{\"semantic_verdict\":false,\"negotiation_type\":null,\"errors\":[{\"slot_name\":"
                                + "\"section.info_static\",\"code\":\"template_type_mismatch\",\"message\":"
                                + "\"inconsistent\"}],\"params\":{}}"))
                .build();
        try {
            orchestrator.validateAndFillingProposeData(
                    "## 协商上下文\n- id: " + UUID + "\n- round: 1\n- maxRounds: 5",
                    Map.of("type", "object"),
                    INFORMATION_PROPOSE_URI);
        } catch (NegotiationParamExtractionException expected) {
            assertEquals(A2ATErrorCodes.NEGOTIATION_SEMANTIC_REJECTED, expected.getCode());
        }
    }

    private void driveRuleViolation() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(validExtractionPayload(), validSemanticPayload()))
                .build();
        try {
            orchestrator.validateAndFillingProposeData(
                    "## 协商上下文\n- id: " + UUID + "\n- round: 9\n- maxRounds: 5",
                    Map.of("type", "object"),
                    INFORMATION_PROPOSE_URI);
        } catch (NegotiationParamExtractionException expected) {
            assertEquals(A2ATErrorCodes.NEGOTIATION_RULE_VIOLATION, expected.getCode());
        }
    }

    private void driveQueryBoundaries() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .templateLoader(missingTemplateLoader())
                .build();
        orchestrator.getNegotiationPrompts();
        orchestrator.getNegotiationPrompt(INFORMATION_PROPOSE_URI);
        assertTrue(orchestrator.getNegotiationPrompt("malformed-template-uri").isEmpty());
    }

    private static String validExtractionPayload() {
        return "{\"items\":[{\"name\":\"节能区域\",\"value\":\"松山湖\"}],\"relationship\":null}";
    }

    private static String validSemanticPayload() {
        return "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                + "\"params\":{\"region\":\"松山湖\"}}";
    }

    private static NegotiationTemplateLoader missingTemplateLoader() {
        return new NegotiationTemplateLoader() {
            @Override
            public PromptTemplate load(NegotiationReference reference) {
                throw new net.openan.a2at.sdk.core.exception.ResourceNotFoundException(
                        "Negotiation template does not exist.", reference.uri());
            }

            @Override
            public List<PromptTemplate> loadAll() {
                throw new net.openan.a2at.sdk.core.exception.ResourceNotFoundException(
                        "No negotiation template exists for the configured language.", "templates/Negotiation-T/v1");
            }
        };
    }

    private Map<String, Set<Level>> negotiationEventLevels() {
        Map<String, Set<Level>> levels = new LinkedHashMap<>();
        for (ILoggingEvent event : negotiationEvents()) {
            levels.computeIfAbsent(eventName(event), ignored -> new java.util.HashSet<>())
                    .add(event.getLevel());
        }
        return levels;
    }

    private List<ILoggingEvent> negotiationEvents() {
        return appender.list.stream()
                .filter(event -> event.getFormattedMessage().startsWith("negotiation_"))
                .toList();
    }

    private List<String> messagesOf(String eventName) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith(eventName + " ") || message.equals(eventName))
                .collect(Collectors.toList());
    }

    private static String eventName(ILoggingEvent event) {
        return event.getFormattedMessage().split(" ")[0];
    }

    private static List<String> keyTokens(String message) {
        return java.util.Arrays.stream(message.split(" "))
                .filter(token -> token.contains("="))
                .map(token -> token.substring(0, token.indexOf('=')))
                .collect(Collectors.toList());
    }

    private static final class ScriptedClient implements LLMClient {

        private final List<String> payloads;

        private int calls;

        private ScriptedClient(String... payloads) {
            this.payloads = List.of(payloads);
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            String payload = payloads.get(Math.min(calls, payloads.size() - 1));
            calls++;
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }

    private static final class FailingClient implements LLMClient {

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            throw new IllegalStateException("LLM endpoint unavailable.");
        }
    }
}
