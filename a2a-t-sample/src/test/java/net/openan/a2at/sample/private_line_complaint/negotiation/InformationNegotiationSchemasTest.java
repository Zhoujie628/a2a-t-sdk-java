package net.openan.a2at.sample.private_line_complaint.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.InformationNegotiationSchemas;
import org.junit.jupiter.api.Test;

class InformationNegotiationSchemasTest {

    @Test
    void proposeSchemaDefinesRequestedComplaintInformation() {
        Map<String, Object> schema = InformationNegotiationSchemas.propose();
        Map<?, ?> properties = propertiesOf(schema);

        assertEquals("object", schema.get("type"));
        assertFalse((Boolean) schema.get("additionalProperties"));
        assertEquals(List.of("access_port_name", "complaint_category"), schema.get("required"));
        assertStringProperty(properties, "access_port_name");
        assertStringProperty(properties, "complaint_category");
        assertThrows(UnsupportedOperationException.class, () -> schema.put("unexpected", true));
    }

    @Test
    void acceptSchemaDefinesSuppliedComplaintInformation() {
        Map<String, Object> schema = InformationNegotiationSchemas.accept();
        Map<?, ?> properties = propertiesOf(schema);

        assertEquals(List.of("access_port_name", "complaint_category"), schema.get("required"));
        assertStringProperty(properties, "access_port_name");
        assertEquals(List.of("专线中断", "专线质差"), ((Map<?, ?>) properties.get("complaint_category")).get("enum"));
    }

    @Test
    void rejectSchemaOnlyRequiresAReason() {
        Map<String, Object> schema = InformationNegotiationSchemas.reject();
        Map<?, ?> properties = propertiesOf(schema);

        assertEquals(List.of("rejection_reason"), schema.get("required"));
        assertStringProperty(properties, "rejection_reason");
    }

    private static void assertStringProperty(Map<?, ?> properties, String name) {
        assertEquals("string", ((Map<?, ?>) properties.get(name)).get("type"));
    }

    private static Map<?, ?> propertiesOf(Map<?, ?> schema) {
        return (Map<?, ?>) schema.get("properties");
    }
}
