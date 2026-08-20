package net.openan.a2at.sdk.negotiation.language;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.negotiation.generation.NegotiationPromptResourceLoader;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs.GoldenCase;
import net.openan.a2at.sdk.negotiation.resources.DefaultNegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.resources.PromptTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Guards the integrity of the bundled negotiation resources from the Java side.
 *
 * <p>The checks mirror the resource contract of the negotiation content layer: the twelve built-in templates must exist
 * and be non-empty, every slot marker line of every template must resolve against the vocabulary of its language (with
 * exactly the three pinned exception slots whose name differs from their section title), the marker script must match
 * the template language, the four LLM prompt categories must exist non-empty for both languages, and no golden fixture
 * may contain a template requirements line or an unreplaced placeholder.
 */
class NegotiationResourceIntegrityTest {

    private static final List<String> PROMPT_CATEGORIES = List.of(
            "information_negotiation",
            "target_negotiation",
            "feasibility_negotiation",
            "negotiation_semantic_validation");

    private static final List<String> EXCEPTION_SLOT_KEYS =
            List.of("slot.feasibility", "slot.target", "slot.feasibility_confirm");

    private static final Pattern ZH_SLOT_LINE = Pattern.compile("^\\{\\{(.+?)\\}\\}（(必填|选填)）$");

    private static final Pattern EN_SLOT_LINE = Pattern.compile("^\\{\\{(.+?)\\}\\} \\((required|optional)\\)$");

    @Test
    void allTwelveTemplatesExistAndAreNonEmptyOnTheClasspath() {
        int templateCount = 0;
        for (String language : GoldenInputs.LANGUAGES) {
            DefaultNegotiationTemplateLoader loader = new DefaultNegotiationTemplateLoader(language, null);
            for (NegotiationType type : NegotiationType.values()) {
                for (NegotiationPhase phase : List.of(NegotiationPhase.PROPOSE, NegotiationPhase.ACCEPT)) {
                    PromptTemplate template = loader.load(new NegotiationReference(type, phase, language));
                    assertFalse(template.content().isBlank());
                    templateCount++;
                }
            }
        }
        assertTrue(templateCount == 12, "exactly 3 types x 2 phases x 2 languages must exist, got " + templateCount);
    }

    @ParameterizedTest(name = "slot markers resolve against the vocabulary and the marker script [{0}]")
    @ValueSource(strings = {GoldenInputs.ZH_CN, GoldenInputs.EN_US})
    void everySlotMarkerResolvesAgainstTheVocabularyOfItsLanguage(String language) {
        Vocabulary vocabulary = Vocabulary.forLanguage(language);
        Map<String, String> valuesToKeys = valuesToKeys(vocabulary);
        Pattern slotLinePattern = GoldenInputs.ZH_CN.equals(language) ? ZH_SLOT_LINE : EN_SLOT_LINE;
        String requirementsMarker = GoldenInputs.ZH_CN.equals(language) ? "要求：" : "Requirements:";
        List<String> slotsDifferingFromTheirTitle = new ArrayList<>();

        for (PromptTemplate template : new DefaultNegotiationTemplateLoader(language, null).loadAll()) {
            for (Section section : sectionsOf(template.content())) {
                if (section.slotMarkerLine == null) {
                    continue;
                }
                Matcher matcher = slotLinePattern.matcher(section.slotMarkerLine);
                assertTrue(
                        matcher.matches(),
                        "the slot marker line of section " + section.title + " in "
                                + template.uri() + " must use the marker script of " + language + ": "
                                + section.slotMarkerLine);
                String slotName = matcher.group(1);
                assertTrue(
                        valuesToKeys.containsKey(slotName),
                        "the slot name " + slotName + " of " + template.uri() + " must be a vocabulary value of "
                                + language);
                assertTrue(
                        valuesToKeys.containsKey(section.title),
                        "the section title " + section.title + " of " + template.uri()
                                + " must be a vocabulary value of " + language);
                if (!slotName.equals(section.title)) {
                    slotsDifferingFromTheirTitle.add(slotName);
                }
                assertTrue(
                        section.bodyLines.stream().anyMatch(line -> line.equals(requirementsMarker)),
                        "every slot section must keep its requirements line: " + section.title + " in "
                                + template.uri());
            }
        }

        List<String> expectedExceptionSlots =
                EXCEPTION_SLOT_KEYS.stream().map(vocabulary::get).sorted().toList();
        List<String> actualExceptionSlots =
                slotsDifferingFromTheirTitle.stream().distinct().sorted().toList();
        assertTrue(
                expectedExceptionSlots.equals(actualExceptionSlots),
                "exactly the three pinned exception slots may differ from their section title but were "
                        + actualExceptionSlots);
    }

    @Test
    void promptResourcesExistAndAreNonEmptyForEveryCategoryAndLanguage() {
        NegotiationPromptResourceLoader loader = new NegotiationPromptResourceLoader();
        for (String category : PROMPT_CATEGORIES) {
            for (String language : GoldenInputs.LANGUAGES) {
                assertFalse(loader.loadSystem(category, language).isBlank(), category + "/" + language + "/system.md");
                assertFalse(loader.loadUser(category, language).isBlank(), category + "/" + language + "/user.md");
            }
        }
    }

    @Test
    void thePromptResourceSurfaceContainsNoTypeRecognitionCategory() throws IOException, URISyntaxException {
        URL promptsRoot = Thread.currentThread().getContextClassLoader().getResource("prompt_resources/prompts");
        assertNotNull(promptsRoot, "the prompt resources root must exist on the classpath");
        if (!"file".equals(promptsRoot.getProtocol())) {
            return;
        }
        List<String> categories = new ArrayList<>();
        try (var directory = Files.newDirectoryStream(Path.of(promptsRoot.toURI()))) {
            directory.forEach(entry -> {
                String name = entry.getFileName().toString();
                if (!name.startsWith(".")) {
                    categories.add(name);
                }
            });
        }
        assertTrue(
                !categories.contains("negotiation_type_recognition"),
                "the removed type-recognition prompt category must not reappear: " + categories);
        for (String category : PROMPT_CATEGORIES) {
            assertTrue(categories.contains(category), "the prompt category " + category + " must exist: " + categories);
        }
    }

    @ParameterizedTest(name = "golden outputs carry no requirements line and no placeholder [{0} {1}]")
    @MethodSource("goldenOutputCases")
    void goldenOutputsNeverContainRequirementsLinesOrPlaceholders(GoldenCase goldenCase, String language) {
        String promptText = readGoldenFixture(goldenCase, language);

        for (String line : promptText.split("\n")) {
            assertFalse(
                    line.equals("要求：") || line.equals("Requirements:"),
                    "a template requirements line must not enter a rendered message: " + line);
        }
        assertFalse(promptText.contains("{{"), "no unreplaced placeholder may remain in a rendered message");
        assertFalse(promptText.contains("<!--"), "the template description comment must not enter a rendered message");
    }

    static List<Arguments> goldenOutputCases() {
        List<Arguments> cases = new ArrayList<>();
        for (String language : GoldenInputs.LANGUAGES) {
            for (GoldenCase goldenCase : GoldenCase.values()) {
                cases.add(Arguments.of(goldenCase, language));
            }
        }
        return cases;
    }

    private static Map<String, String> valuesToKeys(Vocabulary vocabulary) {
        Map<String, String> valuesToKeys = new HashMap<>();
        for (String key : vocabulary.canonicalKeys()) {
            valuesToKeys.put(vocabulary.get(key), key);
        }
        return valuesToKeys;
    }

    private static List<Section> sectionsOf(String templateContent) {
        List<Section> sections = new ArrayList<>();
        Section current = null;
        for (String rawLine : templateContent.split("\n", -1)) {
            String line = rawLine.strip();
            if (line.startsWith("## ")) {
                current = new Section(line.substring(3).strip());
                sections.add(current);
                continue;
            }
            if (current != null) {
                current.bodyLines.add(line);
                if (current.slotMarkerLine == null && line.startsWith("{{")) {
                    current.slotMarkerLine = line;
                }
            }
        }
        return sections;
    }

    private static String readGoldenFixture(GoldenCase goldenCase, String language) {
        String resourcePath = goldenCase.goldenResourcePath(language);
        InputStream stream = NegotiationResourceIntegrityTest.class.getResourceAsStream(resourcePath);
        assertNotNull(stream, "Golden fixture must exist on the test classpath: " + resourcePath);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Failed to read golden fixture " + resourcePath, exception);
        }
    }

    private static final class Section {

        private final String title;

        private final List<String> bodyLines = new ArrayList<>();

        private String slotMarkerLine;

        private Section(String title) {
            this.title = title;
        }
    }
}
