package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.negotiation.content.NegotiationContentException;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationJsonSchemaBuilderTest {

    private final NegotiationJsonSchemaBuilder builder = new NegotiationJsonSchemaBuilder();

    @Test
    void informationProposeSchemaUsesSnakeCasePropertiesAndNullableRelationship() {
        Map<String, Object> schema =
                builder.buildExtractionSchema(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE);

        assertEquals("object", schema.get("type"));
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(List.of("items", "relationship"), List.copyOf(properties.keySet()));
        assertEquals(List.of("items"), schema.get("required"));

        Map<?, ?> relationship = (Map<?, ?>) properties.get("relationship");
        assertEquals(List.of("string", "null"), relationship.get("type"));
        assertFalse(relationship.containsKey("enum"));

        Map<?, ?> items = (Map<?, ?>) properties.get("items");
        assertEquals("array", items.get("type"));
        Map<?, ?> item = (Map<?, ?>) items.get("items");
        assertEquals(List.of("name", "value"), List.copyOf(((Map<?, ?>) item.get("properties")).keySet()));
        assertEquals(List.of("name", "value"), item.get("required"));
    }

    @Test
    void informationEndingSchemaRequiresConclusionAndItems() {
        Map<String, Object> schema =
                builder.buildExtractionSchema(NegotiationType.INFORMATION, NegotiationPhase.ACCEPT);

        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(List.of("conclusion", "items"), List.copyOf(properties.keySet()));
        assertEquals(List.of("conclusion", "items"), schema.get("required"));
        assertEquals(List.of("Accept", "Reject"), ((Map<?, ?>) properties.get("conclusion")).get("enum"));
    }

    @Test
    void acceptAndRejectShareTheTerminalSchema() {
        assertEquals(
                builder.buildExtractionSchema(NegotiationType.TARGET, NegotiationPhase.ACCEPT),
                builder.buildExtractionSchema(NegotiationType.TARGET, NegotiationPhase.REJECT));
    }

    @Test
    void targetProposeSchemaUsesSnakeCaseProperties() {
        Map<String, Object> schema = builder.buildExtractionSchema(NegotiationType.TARGET, NegotiationPhase.PROPOSE);

        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(
                List.of(
                        "target_negotiation_description",
                        "intent_understanding",
                        "alignment_and_clarification",
                        "request_for_clarification"),
                List.copyOf(properties.keySet()));
        assertEquals(List.of("target_negotiation_description"), schema.get("required"));
        assertEquals(List.of("array", "null"), ((Map<?, ?>) properties.get("intent_understanding")).get("type"));
    }

    @Test
    void targetEndingSchemaCarriesNullableResultFields() {
        Map<String, Object> schema = builder.buildExtractionSchema(NegotiationType.TARGET, NegotiationPhase.REJECT);

        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(List.of("conclusion", "confirmed_intent", "failure_reason"), List.copyOf(properties.keySet()));
        assertEquals(List.of("conclusion"), schema.get("required"));
        assertEquals(List.of("string", "null"), ((Map<?, ?>) properties.get("confirmed_intent")).get("type"));
        assertEquals(List.of("string", "null"), ((Map<?, ?>) properties.get("failure_reason")).get("type"));
    }

    @Test
    void feasibilityProposeSchemaRequiresTheActionEnum() {
        Map<String, Object> schema =
                builder.buildExtractionSchema(NegotiationType.FEASIBILITY, NegotiationPhase.PROPOSE);

        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(
                List.of(
                        "feasibility_negotiation_description",
                        "action",
                        "contents_to_evaluate",
                        "infeasibility_details_and_proposal"),
                List.copyOf(properties.keySet()));
        assertEquals(List.of("feasibility_negotiation_description", "action"), schema.get("required"));
        Map<?, ?> action = (Map<?, ?>) properties.get("action");
        assertEquals(List.of("REQUEST_FEASIBILITY_EVALUATION", "PROPOSE_ALTERNATIVE_ON_FAILURE"), action.get("enum"));
    }

    @Test
    void feasibilityEndingSchemaRequiresTheSummary() {
        Map<String, Object> schema =
                builder.buildExtractionSchema(NegotiationType.FEASIBILITY, NegotiationPhase.ACCEPT);

        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(List.of("conclusion", "feasibility_summary"), List.copyOf(properties.keySet()));
        assertEquals(List.of("conclusion", "feasibility_summary"), schema.get("required"));
    }

    @Test
    void rejectsNullTypeAndPhase() {
        assertEquals(
                "type",
                assertThrows(
                                NegotiationContentException.class,
                                () -> builder.buildExtractionSchema(null, NegotiationPhase.PROPOSE))
                        .getField());
        assertEquals(
                "phase",
                assertThrows(
                                NegotiationContentException.class,
                                () -> builder.buildExtractionSchema(NegotiationType.INFORMATION, null))
                        .getField());
    }

    @Test
    @SuppressWarnings("unchecked")
    void semanticValidationSchemaMergesTheCallerSchemaIntoAFourKeyContract() {
        Map<String, Object> callerSchema =
                Map.of("type", "object", "properties", Map.of("energy_rate", Map.of("type", "number")));

        Map<String, Object> schema = builder.buildSemanticValidationSchema(callerSchema);

        assertEquals("object", schema.get("type"));
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertEquals(
                List.of("semantic_verdict", "negotiation_type", "errors", "params"), List.copyOf(properties.keySet()));
        assertEquals(List.of("semantic_verdict", "negotiation_type", "errors", "params"), schema.get("required"));
        assertEquals(Boolean.FALSE, schema.get("additionalProperties"));

        assertEquals(Map.of("type", "boolean"), properties.get("semantic_verdict"));

        Map<String, Object> negotiationType = (Map<String, Object>) properties.get("negotiation_type");
        assertEquals(List.of("string", "null"), negotiationType.get("type"));
        List<Object> typeEnum = (List<Object>) negotiationType.get("enum");
        assertEquals(4, typeEnum.size());
        assertTrue(typeEnum.contains("information"));
        assertTrue(typeEnum.contains("target"));
        assertTrue(typeEnum.contains("feasibility"));
        assertTrue(typeEnum.contains(null));

        Map<String, Object> errors = (Map<String, Object>) properties.get("errors");
        assertEquals("array", errors.get("type"));
        Map<String, Object> errorItem = (Map<String, Object>) errors.get("items");
        assertEquals(
                List.of("slot_name", "code", "message"),
                List.copyOf(((Map<String, Object>) errorItem.get("properties")).keySet()));
        assertEquals(List.of("slot_name", "code", "message"), errorItem.get("required"));

        assertEquals(callerSchema, properties.get("params"));
    }

    @Test
    void semanticValidationSchemaWrapsCallerSchemaWithoutTypeKeyword() {
        Map<String, Object> callerSchema = new java.util.LinkedHashMap<>();
        callerSchema.put("properties", Map.of("id", Map.of("type", "string")));

        Map<String, Object> schema = builder.buildSemanticValidationSchema(callerSchema);

        Map<?, ?> params = (Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("params");
        assertEquals("object", params.get("type"));
        assertEquals(Map.of("id", Map.of("type", "string")), params.get("properties"));
        assertNull(params.get("additionalProperties"));
    }

    @Test
    void rejectsNullCallerSchema() {
        assertEquals(
                "schema",
                assertThrows(NegotiationContentException.class, () -> builder.buildSemanticValidationSchema(null))
                        .getField());
    }
}
