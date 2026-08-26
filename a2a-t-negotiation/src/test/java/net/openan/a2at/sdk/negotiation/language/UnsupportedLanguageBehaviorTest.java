package net.openan.a2at.sdk.negotiation.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.negotiation.resources.DefaultNegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import org.junit.jupiter.api.Test;

/**
 * Locks the behavior of the negotiation content layer for languages without bundled resources.
 *
 * <p>An unsupported language is keyed hard, never silently mapped onto another one: assembling a negotiation pipeline
 * for such a language fails up front with an actionable message pointing at the language configuration, and the
 * template loader of such a language lists no template at all instead of falling back to the built-in templates of
 * another language.
 */
class UnsupportedLanguageBehaviorTest {

    @Test
    void assemblingThePipelineForAnUnsupportedLanguageFailsWithTheLanguageHint() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> NegotiationGenerationOrchestratorBuilder.builder()
                        .language("fr-FR")
                        .build());

        assertTrue(
                exception.getMessage().contains("fr-FR"),
                "the failure must name the unsupported language: " + exception.getMessage());
        assertTrue(
                exception.getMessage().contains("A2AT_LANGUAGE"),
                "the failure must point at the language configuration: " + exception.getMessage());
    }

    @Test
    void templateQueriesOfAnUnsupportedLanguageNeverFallBackToAnotherLanguage() {
        DefaultNegotiationTemplateLoader frFrLoader = new DefaultNegotiationTemplateLoader("fr-FR", null);

        assertEquals(List.of(), frFrLoader.loadAll(), "no template of another language may be listed for fr-FR");
        for (String bundledLanguage : List.of("zh-CN", "en-US")) {
            assertEquals(
                    7,
                    new DefaultNegotiationTemplateLoader(bundledLanguage, null)
                            .loadAll()
                            .size(),
                    "the bundled languages keep their full template set");
        }
    }

    @Test
    void loadingASingleTemplateOfAnUnsupportedLanguageFailsWithTheLanguageHint() {
        DefaultNegotiationTemplateLoader loader = new DefaultNegotiationTemplateLoader("fr-FR", null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> loader.load(
                        new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "fr-FR")));

        assertTrue(
                exception.getMessage().contains("A2AT_LANGUAGE"),
                "the failure must point at the language configuration: " + exception.getMessage());
    }
}
