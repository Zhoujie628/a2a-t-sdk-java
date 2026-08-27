package net.openan.a2at.sample.private_line_complaint.negotiation.shared;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Scenario-specific caller schemas for the private-line complaint information negotiation sample. */
public final class InformationNegotiationSchemas {

    private static final Map<String, Object> PROPOSE = createProposeSchema();

    private static final Map<String, Object> ACCEPT = createAcceptSchema();

    private static final Map<String, Object> REJECT = createRejectSchema();

    private InformationNegotiationSchemas() {
    }

    public static Map<String, Object> propose() {
        return PROPOSE;
    }

    public static Map<String, Object> accept() {
        return ACCEPT;
    }

    public static Map<String, Object> reject() {
        return REJECT;
    }

    private static Map<String, Object> createProposeSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(
                "access_port_name",
                stringSchema("Requested access-port information, including the accepted physical or logical port format"));
        properties.put(
                "complaint_category",
                stringSchema("Requested complaint category and its allowed private-line complaint values"));
        return objectSchema(properties, List.of("access_port_name", "complaint_category"));
    }

    private static Map<String, Object> createAcceptSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(
                "access_port_name",
                stringSchema("Physical or logical access-port name supplied for private-line diagnosis"));
        properties.put(
                "complaint_category",
                Map.of(
                        "type", "string",
                        "description", "Private-line complaint category",
                        "enum", List.of("专线中断", "专线质差")));
        return objectSchema(properties, List.of("access_port_name", "complaint_category"));
    }

    private static Map<String, Object> createRejectSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(
                "access_port_name",
                stringSchema("Reason why the access-port name cannot be supplied"));
        properties.put(
                "complaint_category",
                stringSchema("Reason why the complaint category cannot be supplied"));
        return objectSchema(properties, List.of("access_port_name", "complaint_category"));
    }

    private static Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", Collections.unmodifiableMap(properties));
        schema.put("required", required);
        return Collections.unmodifiableMap(schema);
    }
}
