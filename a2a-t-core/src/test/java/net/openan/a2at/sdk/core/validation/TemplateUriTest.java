package net.openan.a2at.sdk.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TemplateUriTest {

    @Test
    void composesUriFromComponents() {
        TemplateUri uri = TemplateUri.of("Task-T", "v1", "energy-saving");
        assertEquals("Task-T/v1/energy-saving", uri.uri());
        assertEquals("Task-T", uri.extensionName());
        assertEquals("v1", uri.version());
        assertEquals(List.of("energy-saving"), uri.segments());
    }

    @Test
    void parseRoundTripsSingleTrailingSegment() {
        Optional<TemplateUri> parsed = TemplateUri.parse("Task-T/v1/energy-saving");
        assertEquals(Optional.of(TemplateUri.of("Task-T", "v1", "energy-saving")), parsed);
        assertEquals("Task-T/v1/energy-saving", parsed.orElseThrow().uri());
    }

    @Test
    void parseRoundTripsTwoTrailingSegments() {
        Optional<TemplateUri> parsed = TemplateUri.parse("Negotiation-T/v1/feasibility-negotiation/propose");
        assertEquals(
                Optional.of(TemplateUri.of("Negotiation-T", "v1", "feasibility-negotiation", "propose")), parsed);
    }

    @Test
    void parseRejectsMalformedUris() {
        assertEquals(Optional.empty(), TemplateUri.parse(null));
        assertEquals(Optional.empty(), TemplateUri.parse("  "));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T"));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T/v1"));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T/v1/energy-saving/../etc"));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T/v1/energy\\saving"));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T//energy-saving"));
    }

    @Test
    void ofRejectsInvalidComponents() {
        assertThrows(NullPointerException.class, () -> TemplateUri.of(null, "v1", "scenario"));
        assertThrows(NullPointerException.class, () -> TemplateUri.of("Task-T", null, "scenario"));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T", "v1"));
        assertThrows(NullPointerException.class, () -> new TemplateUri("Task-T", "v1", (List<String>) null));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T", "v1", "scen/ario"));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T", "v1", "scen..ario/x"));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T", "v1", "  "));
    }

    @Test
    void segmentsAreDefensivelyCopied() {
        java.util.ArrayList<String> segments = new java.util.ArrayList<>(List.of("energy-saving"));
        TemplateUri uri = new TemplateUri("Task-T", "v1", segments);
        segments.add("tampered");
        assertEquals(List.of("energy-saving"), uri.segments());
    }
}
