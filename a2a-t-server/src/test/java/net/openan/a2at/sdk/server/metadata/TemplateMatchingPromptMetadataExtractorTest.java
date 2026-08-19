package net.openan.a2at.sdk.server.metadata;

import net.openan.a2at.sdk.server.exception.PromptComplianceCheckException;
import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;
import net.openan.a2at.sdk.server.model.PromptTemplateDefinition;
import net.openan.a2at.sdk.server.model.PromptTemplateSlotDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.prompt.taskrendering.api.TaskPromptRenderer;
import org.junit.jupiter.api.Test;

class TemplateMatchingPromptMetadataExtractorTest {

    @Test
    void extractReturnsScenarioLanguageTemplateAndSlotsForMatchedTemplate() {
        TemplateMatchingPromptMetadataExtractor extractor =
                new TemplateMatchingPromptMetadataExtractor(List.of(new PromptTemplateDefinition(
                        "energy-saving",
                        "en-US",
                        "Site: {site}\nNotes: {additional_notes}",
                        List.of(
                                new PromptTemplateSlotDefinition("site", true),
                                new PromptTemplateSlotDefinition("additional_notes", false)))));

        ProcessedPromptMetadata metadata = extractor.extract("Site: Site A\nNotes: critical");

        assertEquals("energy-saving", metadata.scenarioCode());
        assertEquals("en-US", metadata.language());
        assertEquals("Site: {site}\nNotes: {additional_notes}", metadata.templateText());
        assertEquals(Map.of("site", "Site A", "additional_notes", "critical"), metadata.slots());
    }

    @Test
    void extractRejectsPromptThatDoesNotMatchAnyKnownTemplate() {
        TemplateMatchingPromptMetadataExtractor extractor =
                new TemplateMatchingPromptMetadataExtractor(List.of(new PromptTemplateDefinition(
                        "energy-saving",
                        "en-US",
                        "Site: {site}",
                        List.of(new PromptTemplateSlotDefinition("site", true)))));

        PromptComplianceCheckException error =
                assertThrows(PromptComplianceCheckException.class, () -> extractor.extract("Unknown prompt"));

        assertEquals("processed_prompt_parse_error", error.code());
        assertEquals("prompt_parse", error.stage());
    }

    @Test
    void extractRejectsMissingRequiredSlotValueAfterTemplateMatch() {
        TemplateMatchingPromptMetadataExtractor extractor =
                new TemplateMatchingPromptMetadataExtractor(List.of(new PromptTemplateDefinition(
                        "energy-saving",
                        "en-US",
                        "Site: {site}",
                        List.of(new PromptTemplateSlotDefinition("site", true)))));

        PromptComplianceCheckException error =
                assertThrows(PromptComplianceCheckException.class, () -> extractor.extract("Site: "));

        assertEquals("slot_validation_error", error.code());
        assertEquals("slot_validation", error.stage());
    }

    @Test
    void extractMatchesTemplateWithDoubleBracedPlaceholders() {
        TemplateMatchingPromptMetadataExtractor extractor =
                new TemplateMatchingPromptMetadataExtractor(List.of(new PromptTemplateDefinition(
                        "subscribe-incident",
                        "en-US",
                        "Topic: {{topic}}\nCondition: {{condition}}",
                        List.of(
                                new PromptTemplateSlotDefinition("topic", true),
                                new PromptTemplateSlotDefinition("condition", false)))));

        ProcessedPromptMetadata metadata = extractor.extract(
                "Topic: Incident\nCondition: Severity is critical; alert type is flash");

        assertEquals("subscribe-incident", metadata.scenarioCode());
        assertEquals("en-US", metadata.language());
        assertEquals(
                Map.of(
                        "topic", "Incident",
                        "condition", "Severity is critical; alert type is flash"),
                metadata.slots());
    }

    @Test
    void extractMatchesCollapsedPromptAgainstOriginalTemplateWithDescriptiveLines() {
        String originalTemplate =
                "## Subscription Description\n"
                        + "Use the following topic, condition, report format, and expected output to complete an incident subscription task.\n\n"
                        + "## Topic\n"
                        + "{{topic}} (Required)\n"
                        + "Requirement: provide the incident topic name, such as Incident or Fault.\n\n"
                        + "## Condition\n"
                        + "{{condition}} (Optional)\n"
                        + "Requirement: include fault severity and fault name when needed.\n"
                        + "Severity supports values such as critical, high, medium, and low.\n"
                        + "Fault name supports network-side fault names such as pigtail failure, fiber cut, board failure, and optical module failure.\n\n"
                        + "## Report Format\n"
                        + "{{report_format}} (Optional)\n"
                        + "Requirement: specify the reported data type and the A2A Part type, such as DataPart or TextPart.\n"
                        + "Example: report incident data through DataPart.\n\n"
                        + "## Expected Output\n"
                        + "1. Subscription result, success or failure\n"
                        + "2. Failure reason when the subscription fails";
        String processedPrompt =
                "## Subscription Description\n"
                        + "Use the following topic, condition, report format, and expected output to complete an incident subscription task.\n\n"
                        + "## Topic\n"
                        + "Incident\n\n"
                        + "## Condition\n"
                        + "Subscribe to ETH-LOS faults with critical severity\n\n"
                        + "## Report Format\n"
                        + "DataPart\n\n"
                        + "## Expected Output\n"
                        + "1. Subscription result, success or failure\n"
                        + "2. Failure reason when the subscription fails";
        Map<String, String> sentinelSlots = new LinkedHashMap<>();
        sentinelSlots.put("topic", "__A2AT_SLOT_0__");
        sentinelSlots.put("condition", "__A2AT_SLOT_1__");
        sentinelSlots.put("report_format", "__A2AT_SLOT_2__");

        assertEquals(
                "## Subscription Description\n"
                        + "Use the following topic, condition, report format, and expected output to complete an incident subscription task.\n\n"
                        + "## Topic\n"
                        + "__A2AT_SLOT_0__\n\n"
                        + "## Condition\n"
                        + "__A2AT_SLOT_1__\n\n"
                        + "## Report Format\n"
                        + "__A2AT_SLOT_2__\n\n"
                        + "## Expected Output\n"
                        + "1. Subscription result, success or failure\n"
                        + "2. Failure reason when the subscription fails",
                new TaskPromptRenderer().render(originalTemplate, sentinelSlots));

        TemplateMatchingPromptMetadataExtractor extractor =
                new TemplateMatchingPromptMetadataExtractor(List.of(new PromptTemplateDefinition(
                        "subscribe-incident",
                        "en-US",
                        originalTemplate,
                        List.of(
                                new PromptTemplateSlotDefinition("topic", true),
                                new PromptTemplateSlotDefinition("condition", false),
                                new PromptTemplateSlotDefinition("report_format", false)))));

        ProcessedPromptMetadata metadata = extractor.extract(processedPrompt);

        assertEquals("subscribe-incident", metadata.scenarioCode());
        assertEquals("en-US", metadata.language());
        assertEquals(
                Map.of(
                        "topic", "Incident",
                        "condition", "Subscribe to ETH-LOS faults with critical severity",
                        "report_format", "DataPart"),
                metadata.slots());
    }
}
