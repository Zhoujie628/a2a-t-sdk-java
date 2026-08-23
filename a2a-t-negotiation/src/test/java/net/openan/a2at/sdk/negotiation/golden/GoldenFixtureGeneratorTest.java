package net.openan.a2at.sdk.negotiation.golden;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Regeneration helper for the golden fixture set of the negotiation content layer.
 *
 * <p>The test writes the 20 golden fixture files (9 type/phase combinations plus the common abort fixture, x 2
 * languages) under {@code src/test/resources/golden/} by rendering the fixed {@link GoldenInputs} through an
 * orchestrator wired with the real built-in resources. It is disabled in normal builds: golden fixtures are locked by
 * {@link GoldenFixtureComparisonTest} and may only change together with a reviewed template or vocabulary revision. To
 * regenerate, temporarily enable this test, run it from the {@code a2a-t-negotiation} module directory and review the
 * resulting files before committing them.
 */
@Disabled("Golden fixture regeneration helper: enable manually only for a reviewed template or vocabulary change.")
class GoldenFixtureGeneratorTest {

    private static final Path GOLDEN_ROOT = Path.of("src", "test", "resources", "golden");

    @Test
    void writeAllGoldenFixtures() throws IOException {
        if (!Files.isDirectory(GOLDEN_ROOT.getParent())) {
            throw new IllegalStateException(
                    "Golden fixtures can only be regenerated from the a2a-t-negotiation module directory but the"
                            + " test resources directory does not exist: "
                            + GOLDEN_ROOT.getParent().toAbsolutePath());
        }
        for (String language : GoldenInputs.LANGUAGES) {
            NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                    .language(language)
                    .build();
            for (GoldenInputs.GoldenCase goldenCase : GoldenInputs.GoldenCase.values()) {
                MetadataContent result = goldenCase.generate(orchestrator);
                Path target = GOLDEN_ROOT.resolve(language).resolve(goldenCase.fileName());
                Files.createDirectories(target.getParent());
                Files.writeString(target, result.promptText(), StandardCharsets.UTF_8);
            }
        }
    }
}
