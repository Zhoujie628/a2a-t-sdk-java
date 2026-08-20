package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs.GoldenCase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies the rendered format of the negotiation context section of every from-data message.
 *
 * <p>The context section is a three-line markdown list whose field names are language-neutral camelCase identifiers:
 * {@code - id: <uuid>}, {@code - round: <n>} and {@code - maxRounds: <n>}, in exactly this order, regardless of the
 * message language or the negotiation type and phase.
 */
class NegotiationContextSectionRenderingTest {

    static Stream<Arguments> goldenCases() {
        List<Arguments> cases = new ArrayList<>();
        for (String language : GoldenInputs.LANGUAGES) {
            for (GoldenCase goldenCase : GoldenCase.values()) {
                cases.add(Arguments.of(goldenCase, language));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} [{1}] renders the context as a three-line markdown list")
    @MethodSource("goldenCases")
    void rendersTheContextAsAThreeLineMarkdownList(GoldenCase goldenCase, String language) {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .build();

        MetadataContent result = goldenCase.generate(orchestrator);
        String contextTitle = "zh-CN".equals(language) ? "协商上下文" : "Negotiation Context";
        String expectedSection = "## " + contextTitle + "\n- id: " + GoldenInputs.SESSION_ID + "\n- round: "
                + goldenCase.context().round() + "\n- maxRounds: "
                + goldenCase.context().maxRounds();

        assertTrue(
                result.promptText().contains(expectedSection), "the context section must be the fixed three-line list");
        assertEquals(
                1,
                countOccurrences(result.promptText(), expectedSection),
                "the context section must appear exactly once");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
