package net.openan.a2at.sdk.negotiation.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParamSchemasTest {

    @Test
    void builtSchemaMatchesThePinnedStructureExactly() {
        Map<String, Object> expectedProperties = new LinkedHashMap<>();
        expectedProperties.put("id", Map.of("type", "string"));
        expectedProperties.put("round", Map.of("type", "integer"));
        expectedProperties.put("maxRounds", Map.of("type", "integer"));

        Map<String, Object> expectedSchema = new LinkedHashMap<>();
        expectedSchema.put("type", "object");
        expectedSchema.put("properties", expectedProperties);
        expectedSchema.put("additionalProperties", true);

        assertEquals(expectedSchema, NegotiationParamSchemas.buildNegotiationParamSchema());
    }

    @Test
    void builtSchemaSerializesToThePinnedBytes() throws Exception {
        String serialized =
                new ObjectMapper().writeValueAsString(NegotiationParamSchemas.buildNegotiationParamSchema());

        assertEquals(
                "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},"
                        + "\"round\":{\"type\":\"integer\"},\"maxRounds\":{\"type\":\"integer\"}},"
                        + "\"additionalProperties\":true}",
                serialized);
    }

    @Test
    void builtSchemaKeepsThePinnedKeyOrder() {
        Map<String, Object> schema = NegotiationParamSchemas.buildNegotiationParamSchema();

        assertEquals(List.of("type", "properties", "additionalProperties"), new ArrayList<>(schema.keySet()));
        assertEquals(
                List.of("id", "round", "maxRounds"), new ArrayList<>(((Map<?, ?>) schema.get("properties")).keySet()));
    }
}
