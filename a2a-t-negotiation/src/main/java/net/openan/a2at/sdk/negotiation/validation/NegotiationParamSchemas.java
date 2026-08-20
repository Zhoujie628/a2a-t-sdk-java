package net.openan.a2at.sdk.negotiation.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in parameter schemas for negotiation parameter extraction.
 *
 * <p>Callers must always provide a parameter schema explicitly; the schema built here is a convenience constant for the
 * plain negotiation context parameters and remains open for additional properties, so callers may tighten or replace it
 * freely.
 *
 * @since 2026-06
 */
public final class NegotiationParamSchemas {

    private NegotiationParamSchemas() {}

    /**
     * Builds the built-in negotiation parameter schema.
     *
     * @return schema describing an object with the string property id and the integer properties round and maxRounds,
     *     allowing additional properties
     */
    public static Map<String, Object> buildNegotiationParamSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "string"));
        properties.put("round", Map.of("type", "integer"));
        properties.put("maxRounds", Map.of("type", "integer"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.unmodifiableMap(properties));
        schema.put("additionalProperties", true);
        return Collections.unmodifiableMap(schema);
    }
}
