package net.openan.a2at.sdk.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TemplateUriTest {

    @Test
    void composesUriFromComponents() {
        TemplateUri uri = TemplateUri.of("Task-T", "v1", "network-layer", "energy-saving");
        assertEquals("Task-T/network-layer/energy-saving/v1", uri.uri());
        assertEquals("Task-T", uri.extensionName());
        assertEquals(List.of("network-layer", "energy-saving"), uri.pathSegments());
        assertEquals("v1", uri.templateVersion());
    }

    @Test
    void parseRoundTripsNetworkLayerUri() {
        Optional<TemplateUri> parsed = TemplateUri.parse("Task-T/network-layer/energy-saving/v1");
        assertEquals(
                Optional.of(TemplateUri.of("Task-T", "v1", "network-layer", "energy-saving")), parsed);
        assertEquals("Task-T/network-layer/energy-saving/v1", parsed.orElseThrow().uri());
    }

    @Test
    void parseRoundTripsAuthorizationUri() {
        Optional<TemplateUri> parsed = TemplateUri.parse("Authorization-T/authorization-policy-management/v1");
        assertEquals(
                Optional.of(TemplateUri.of("Authorization-T", "v1", "authorization-policy-management")), parsed);
    }

    @Test
    void parseRoundTripsNegotiationUri() {
        Optional<TemplateUri> parsed = TemplateUri.parse("Negotiation-T/information-negotiation/propose/v1");
        assertEquals(
                Optional.of(TemplateUri.of("Negotiation-T", "v1", "information-negotiation", "propose")), parsed);
    }

    @Test
    void parseRejectsMalformedUris() {
        assertEquals(Optional.empty(), TemplateUri.parse(null));
        assertEquals(Optional.empty(), TemplateUri.parse("  "));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T"));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T/v1"));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T/network-layer/energy-saving/../etc"));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T/network-layer/energy\\saving/v1"));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T//energy-saving/v1"));
    }

    @Test
    void ofRejectsInvalidComponents() {
        assertThrows(NullPointerException.class, () -> TemplateUri.of(null, "v1", "scenario"));
        assertThrows(NullPointerException.class, () -> TemplateUri.of("Task-T", null, "scenario"));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T", "v1"));
        assertThrows(NullPointerException.class, () -> new TemplateUri("Task-T", (List<String>) null, "v1"));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T", "v1", "scen/ario"));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T", "v1", "scen..ario/x"));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T", "v1", "  "));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T", "  ", "scenario"));
    }

    @Test
    void pathSegmentsAreDefensivelyCopied() {
        ArrayList<String> segments = new ArrayList<>(List.of("network-layer", "energy-saving"));
        TemplateUri uri = new TemplateUri("Task-T", segments, "v1");
        segments.add("tampered");
        assertEquals(List.of("network-layer", "energy-saving"), uri.pathSegments());
    }
}
