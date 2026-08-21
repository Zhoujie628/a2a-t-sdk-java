package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.validation.StandardTemplates;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

/**
 * Verifies the boundary behavior of the template query API of the negotiation content layer.
 *
 * <p>The list query returns exactly the six built-in templates of the configured language in the fixed type and phase
 * order; the single-template query resolves a valid URI into its template record, and answers a missing or malformed
 * URI with an empty result plus a {@code negotiation_template_not_found} warning that mentions the language
 * configuration hint. Neither query ever throws.
 */
class NegotiationTemplateQueryBoundaryTest {

    private static final List<String> EXPECTED_URI_ORDER = List.of(
            StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri(),
            StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT.uri(),
            StandardTemplates.TARGET_NEGOTIATION_PROPOSE.uri(),
            StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT.uri(),
            StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE.uri(),
            StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT.uri());

    private ListAppender<ILoggingEvent> logAppender;

    private ch.qos.logback.classic.Logger orchestratorLogger;

    @BeforeEach
    void attachLogAppender() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        orchestratorLogger = loggerContext.getLogger(NegotiationGenerationOrchestrator.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        orchestratorLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        orchestratorLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    static Stream<Arguments> languages() {
        return Stream.of(Arguments.of("zh-CN"), Arguments.of("en-US"));
    }

    @ParameterizedTest(name = "list query returns six templates in the fixed order [{0}]")
    @MethodSource("languages")
    void listQueryReturnsSixTemplatesInTheFixedOrder(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        List<PromptTemplate> templates = orchestrator.getNegotiationPrompts();

        assertEquals(6, templates.size());
        assertEquals(
                EXPECTED_URI_ORDER, templates.stream().map(PromptTemplate::uri).toList());
        for (PromptTemplate template : templates) {
            String typeSegment = template.uri().split("/")[2];
            assertFalse(typeSegment.contains("_"), "type segments must use hyphens: " + template.uri());
            assertTrue(typeSegment.endsWith("-negotiation"), "type segment must carry the suffix: " + template.uri());
            assertFalse(template.content().isBlank());
        }
    }

    @ParameterizedTest(name = "single query resolves a valid URI into its template [{0}]")
    @MethodSource("languages")
    void singleQueryResolvesAValidUriIntoItsTemplate(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        Optional<PromptTemplate> template =
                orchestrator.getNegotiationPrompt(StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE.uri());

        assertTrue(template.isPresent());
        assertEquals(
                StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE.uri(),
                template.orElseThrow().uri());
        String contextTitle = "zh-CN".equals(language) ? "## 协商上下文" : "## Negotiation Context";
        assertTrue(template.orElseThrow().content().contains(contextTitle));
        assertTrue(
                template.orElseThrow().content().startsWith("## "), "templates start directly with the first section");
        assertEquals("", template.orElseThrow().description(), "templates carry no description comment");
    }

    @Test
    void singleQueryAnswersAMissingUriWithEmptyAndAWarning() {
        NegotiationGenerationOrchestrator orchestrator = orchestrator("zh-CN");

        Optional<PromptTemplate> template =
                orchestrator.getNegotiationPrompt("Negotiation-T/v1/unknown-negotiation/propose");

        assertTrue(template.isEmpty());
        assertTrue(hasWarning("negotiation_template_not_found", "A2AT_LANGUAGE"));
    }

    @Test
    void singleQueryAnswersAMalformedUriWithEmptyAndAWarning() {
        NegotiationGenerationOrchestrator orchestrator = orchestrator("zh-CN");

        Optional<PromptTemplate> template = orchestrator.getNegotiationPrompt("malformed-uri");

        assertTrue(template.isEmpty());
        assertTrue(hasWarning("negotiation_template_not_found", "A2AT_LANGUAGE"));
    }

    /**
     * Locks the description convention: the built-in templates carry no leading HTML comment, so every template reports
     * an empty description regardless of language.
     */
    @Test
    void builtinTemplatesReportAnEmptyDescription() {
        NegotiationGenerationOrchestrator orchestrator = orchestrator("en-US");

        List<PromptTemplate> templates = orchestrator.getNegotiationPrompts();

        for (PromptTemplate template : templates) {
            assertTrue(template.description().isBlank(), "built-in templates carry no description comment");
        }
    }

    private boolean hasWarning(String eventName, String... messageParts) {
        for (ILoggingEvent event : logAppender.list) {
            if (event.getLevel() != Level.WARN || !event.getFormattedMessage().contains(eventName)) {
                continue;
            }
            boolean allPartsPresent = true;
            for (String part : messageParts) {
                allPartsPresent = allPartsPresent && event.getFormattedMessage().contains(part);
            }
            if (allPartsPresent) {
                return true;
            }
        }
        return false;
    }

    private static NegotiationGenerationOrchestrator orchestrator(String language) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .build();
    }
}
