package net.openan.a2at.sdk.negotiation.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs.GoldenCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Locks the dual-root fallback of the negotiation vocabulary at the orchestrator level.
 *
 * <p>A vocabulary file placed under a local resource root at
 * {@code negotiation-vocabulary/{language}/vocabulary.json} wins over the built-in classpath vocabulary and changes the
 * rendered generator output end to end, while a blank local root keeps the built-in golden output byte for byte. The
 * override mirrors the template-override regime proven by {@link CustomRootTemplateOverrideTest}.
 */
class CustomRootVocabularyOverrideTest {

    private static final String CUSTOM_RELATIONSHIP_LABEL = "自定义缺失项关系：";

    @TempDir
    Path customRoot;

    @Test
    void customRootVocabularyChangesTheRenderedRelationshipLine() throws IOException {
        writeCustomizedZhCnVocabulary();

        String customized = GoldenCase.INFORMATION_PROPOSE.generate(orchestratorWithCustomRoot(), GoldenInputs.ZH_CN)
                .promptText();
        String builtin = GoldenCase.INFORMATION_PROPOSE
                .generate(orchestrator(GoldenInputs.ZH_CN, null), GoldenInputs.ZH_CN)
                .promptText();

        assertTrue(customized.contains(CUSTOM_RELATIONSHIP_LABEL), "the custom label must enter the rendered message");
        assertFalse(builtin.contains(CUSTOM_RELATIONSHIP_LABEL), "the built-in output must keep the bundled label");
        assertEquals(
                builtin.replace(Vocabulary.forLanguage(GoldenInputs.ZH_CN).get("label.relationship"), CUSTOM_RELATIONSHIP_LABEL),
                customized,
                "only the vocabulary-driven relationship label may differ from the built-in output");
    }

    @Test
    void blankLocalRootKeepsTheBuiltInGoldenOutput() throws IOException {
        writeCustomizedZhCnVocabulary();

        MetadataContent withBlankRoot = GoldenCase.INFORMATION_PROPOSE.generate(
                NegotiationGenerationOrchestratorBuilder.builder()
                        .language(GoldenInputs.ZH_CN)
                        .localRootDir("   ")
                        .build(),
                GoldenInputs.ZH_CN);

        assertEquals(
                GoldenCase.INFORMATION_PROPOSE
                        .generate(orchestrator(GoldenInputs.ZH_CN, null), GoldenInputs.ZH_CN)
                        .promptText(),
                withBlankRoot.promptText(),
                "a blank local root must fall back to the classpath vocabulary");
    }

    private void writeCustomizedZhCnVocabulary() throws IOException {
        Vocabulary builtin = Vocabulary.forLanguage(GoldenInputs.ZH_CN);
        Map<String, String> entries = new LinkedHashMap<>();
        for (String key : Vocabulary.CANONICAL_KEYS) {
            entries.put(key, builtin.get(key));
        }
        entries.put("label.relationship", CUSTOM_RELATIONSHIP_LABEL);
        Path file = customRoot
                .resolve("negotiation-vocabulary")
                .resolve(GoldenInputs.ZH_CN)
                .resolve("vocabulary.json");
        Files.createDirectories(file.getParent());
        new ObjectMapper().writeValue(file.toFile(), entries);
    }

    private NegotiationGenerationOrchestrator orchestratorWithCustomRoot() {
        return orchestrator(GoldenInputs.ZH_CN, customRoot.toString());
    }

    private static NegotiationGenerationOrchestrator orchestrator(String language, String localRootDir) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .localRootDir(localRootDir)
                .build();
    }
}
