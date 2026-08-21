package net.openan.a2at.sdk.negotiation.generation;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;

/**
 * Builds the JSON Schemas of the negotiation LLM steps.
 *
 * <p>Extraction schemas are keyed by the snake_case property names of the extracted content, one schema per
 * (negotiation type, phase) pair with accept and reject sharing the terminal schema. The semantic validation schema
 * merges a caller-provided parameter schema into the fixed four-key output contract of the semantic validation step.
 *
 * @since 2026-06
 */
public class NegotiationJsonSchemaBuilder {

    private static final List<String> CONCLUSION_ENUM = List.of("Accept", "Reject");

    private static final List<String> ACTION_ENUM =
            List.of("REQUEST_FEASIBILITY_EVALUATION", "PROPOSE_ALTERNATIVE_ON_FAILURE");

    private static final List<String> NEGOTIATION_TYPE_ENUM =
            Arrays.asList("information", "target", "feasibility", null);

    /**
     * Builds the content extraction schema of one (negotiation type, phase) pair.
     *
     * @param type negotiation type whose content is extracted
     * @param phase API-level phase of the extraction; accept and reject share the terminal schema
     * @return JSON Schema describing the snake_case extraction output of the pair
     * @throws NullPointerException if the type or phase is null
     */
    public Map<String, Object> buildExtractionSchema(NegotiationType type, NegotiationPhase phase) {
        Objects.requireNonNull(type, "Negotiation type must not be null.");
        Objects.requireNonNull(phase, "Negotiation phase must not be null.");
        return switch (type) {
            case INFORMATION -> phase == NegotiationPhase.PROPOSE
                    ? informationProposeSchema()
                    : informationEndingSchema();
            case TARGET -> phase == NegotiationPhase.PROPOSE ? targetProposeSchema() : targetEndingSchema();
            case FEASIBILITY -> phase == NegotiationPhase.PROPOSE
                    ? feasibilityProposeSchema()
                    : feasibilityEndingSchema();
        };
    }

    /**
     * Builds the merged schema of the semantic validation step around a caller-provided parameter schema.
     *
     * <p>The merged schema requires exactly the four keys {@code semantic_verdict}, {@code negotiation_type},
     * {@code errors} and {@code params} and allows no additional properties. The caller schema is embedded as the
     * {@code params} property; a caller schema without a {@code type} keyword is wrapped as an object schema first.
     *
     * @param callerSchema parameter schema provided by the caller of the validation API
     * @return merged JSON Schema of the semantic validation LLM call
     * @throws NullPointerException if the caller schema is null
     */
    public Map<String, Object> buildSemanticValidationSchema(Map<String, Object> callerSchema) {
        Objects.requireNonNull(callerSchema, "Caller parameter schema must not be null.");
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("semantic_verdict", Map.of("type", "boolean"));
        Map<String, Object> negotiationType = new LinkedHashMap<>();
        negotiationType.put("type", List.of("string", "null"));
        negotiationType.put("enum", NEGOTIATION_TYPE_ENUM);
        properties.put("negotiation_type", negotiationType);
        properties.put("errors", errorsSchema());
        properties.put("params", wrapCallerSchema(callerSchema));
        schema.put("properties", properties);
        schema.put("required", List.of("semantic_verdict", "negotiation_type", "errors", "params"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> informationProposeSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("items", itemArraySchema());
        properties.put("relationship", nullableStringSchema());
        return objectSchema(properties, List.of("items"));
    }

    private static Map<String, Object> informationEndingSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("conclusion", conclusionSchema());
        properties.put("items", itemArraySchema());
        return objectSchema(properties, List.of("conclusion", "items"));
    }

    private static Map<String, Object> targetProposeSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("target_negotiation_description", Map.of("type", "string"));
        properties.put("intent_understanding", nullableItemArraySchema());
        properties.put("alignment_and_clarification", nullableItemArraySchema());
        properties.put("request_for_clarification", nullableItemArraySchema());
        return objectSchema(properties, List.of("target_negotiation_description"));
    }

    private static Map<String, Object> targetEndingSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("conclusion", conclusionSchema());
        properties.put("confirmed_intent", nullableStringSchema());
        properties.put("failure_reason", nullableStringSchema());
        return objectSchema(properties, List.of("conclusion"));
    }

    private static Map<String, Object> feasibilityProposeSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("feasibility_negotiation_description", Map.of("type", "string"));
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "string");
        action.put("enum", ACTION_ENUM);
        properties.put("action", action);
        properties.put("contents_to_evaluate", nullableItemArraySchema());
        properties.put("infeasibility_details_and_proposal", nullableItemArraySchema());
        return objectSchema(properties, List.of("feasibility_negotiation_description", "action"));
    }

    private static Map<String, Object> feasibilityEndingSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("conclusion", conclusionSchema());
        properties.put("feasibility_summary", Map.of("type", "string"));
        return objectSchema(properties, List.of("conclusion", "feasibility_summary"));
    }

    private static Map<String, Object> errorsSchema() {
        Map<String, Object> errorProperties = new LinkedHashMap<>();
        errorProperties.put("slot_name", Map.of("type", "string"));
        errorProperties.put("code", Map.of("type", "string"));
        errorProperties.put("message", Map.of("type", "string"));
        Map<String, Object> errorItem = new LinkedHashMap<>();
        errorItem.put("type", "object");
        errorItem.put("properties", errorProperties);
        errorItem.put("required", List.of("slot_name", "code", "message"));
        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("type", "array");
        errors.put("items", errorItem);
        return errors;
    }

    private static Map<String, Object> wrapCallerSchema(Map<String, Object> callerSchema) {
        if (callerSchema.containsKey("type")) {
            return callerSchema;
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("type", "object");
        wrapped.putAll(callerSchema);
        return wrapped;
    }

    private static Map<String, Object> itemArraySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", itemSchema());
        return schema;
    }

    private static Map<String, Object> nullableItemArraySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", List.of("array", "null"));
        schema.put("items", itemSchema());
        return schema;
    }

    private static Map<String, Object> itemSchema() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("name", Map.of("type", "string"));
        itemProperties.put("value", nullableStringSchema());
        return objectSchema(itemProperties, List.of("name", "value"));
    }

    private static Map<String, Object> conclusionSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("enum", CONCLUSION_ENUM);
        return schema;
    }

    private static Map<String, Object> nullableStringSchema() {
        return Map.of("type", List.of("string", "null"));
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }
}
