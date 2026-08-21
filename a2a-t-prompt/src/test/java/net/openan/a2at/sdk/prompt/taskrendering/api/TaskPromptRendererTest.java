package net.openan.a2at.sdk.prompt.taskrendering.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import net.openan.a2at.sdk.prompt.taskrendering.exception.TaskPromptRenderException;
import org.junit.jupiter.api.Test;

class TaskPromptRendererTest {

    private final TaskPromptRenderer renderer = new TaskPromptRenderer();

    @Test
    void renderBuildsPlainPromptBody() {
        String prompt = renderer.render(
                "Site: {site}\nNotes: {additional_notes}", Map.of("site", "Site A", "additional_notes", ""));

        assertEquals("Site: Site A\nNotes: ", prompt);
    }

    @Test
    void renderSupportsDoubleBracedPlaceholders() {
        String prompt = renderer.render(
                "Topic: {{topic}}\nCondition: {{condition}}",
                Map.of("topic", "Incident", "condition", "critical alert"));

        assertEquals("Topic: Incident\nCondition: critical alert", prompt);
    }

    @Test
    void renderSupportsDoubleBracedPlaceholdersInLongerTemplate() {
        String prompt =
                renderer.render(
                        "Topic: {{topic}}\nCondition: {{condition}}",
                        Map.of("topic", "Incident", "condition", "Severity is critical"));

        assertEquals("Topic: Incident\nCondition: Severity is critical", prompt);
    }

    @Test
    void renderCollapsesSectionBodyWhenFirstEffectiveLineIsStandaloneSlotWithEnglishSuffix() {
        String prompt = renderer.render(
                "## Task Type\n"
                        + "Diagnosis\n\n"
                        + "## Task Target\n"
                        + "{{task_target}}(Required)\n\n"
                        + "Requirement: explain the target.\n"
                        + "Example: complete the diagnosis.\n\n"
                        + "## Expected Output\n"
                        + "{{expected_output}}(Optional)\n",
                Map.of(
                        "task_target", "Complete the diagnosis and provide remediation advice.",
                        "expected_output", "Return a structured diagnosis result."));

        assertEquals(
                "## Task Type\n"
                        + "Diagnosis\n\n"
                        + "## Task Target\n"
                        + "Complete the diagnosis and provide remediation advice.\n\n"
                        + "## Expected Output\n"
                        + "Return a structured diagnosis result.\n",
                prompt);
    }

    @Test
    void renderCollapsesSectionBodyWhenFirstEffectiveLineIsStandaloneSlotWithParenthesizedSuffix() {
        String prompt = renderer.render(
                "## Task Target\n"
                        + "{{task_target}} (Required)\n\n"
                        + "Requirement: explain the target.\n"
                        + "Example: complete the diagnosis.\n\n"
                        + "## Expected Output\n"
                        + "{{expected_output}} (Optional)\n",
                Map.of(
                        "task_target", "Complete the fault diagnosis and provide remediation advice.",
                        "expected_output", "Return a structured diagnosis result."));

        assertEquals(
                "## Task Target\n"
                        + "Complete the fault diagnosis and provide remediation advice.\n\n"
                        + "## Expected Output\n"
                        + "Return a structured diagnosis result.\n",
                prompt);
    }

    @Test
    void renderPreservesRegularInlinePlaceholderContent() {
        String prompt = renderer.render(
                "## Subscription\n"
                        + "Please subscribe to {{topic}} incidents.\n\n"
                        + "## Condition\n"
                        + "{{condition}} (Optional)\n"
                        + "Requirement: describe the filter.\n",
                Map.of("topic", "network", "condition", "critical only"));

        assertEquals(
                "## Subscription\n"
                        + "Please subscribe to network incidents.\n\n"
                        + "## Condition\n"
                        + "critical only\n",
                prompt);
    }

    @Test
    void renderRaisesWhenTemplateReferencesUnknownSlot() {
        assertThrows(
                TaskPromptRenderException.class,
                () -> renderer.render("Site: {site}\nTime Range: {time_range}", Map.of("site", "Site A")));
    }

    @Test
    void renderRaisesNullPointerExceptionWhenTemplateIsNull() {
        NullPointerException exception =
                assertThrows(NullPointerException.class, () -> renderer.render(null, Map.of("site", "Site A")));

        assertEquals("Template text must not be null.", exception.getMessage());
    }

    @Test
    void renderRaisesWhenTemplateHasUnbalancedBraces() {
        assertThrows(
                TaskPromptRenderException.class, () -> renderer.render("Site: {{site}", Map.of("site", "Site A")));
    }
}
