package net.openan.a2at.sdk.negotiation.validation;

import java.util.Map;

/**
 * Builds the merged JSON schema used for the semantic validation structured LLM call.
 *
 * <p>The merged schema is the semantic validation output contract: an object with the four required keys
 * {@code semantic_verdict} (boolean), {@code negotiation_type} (string or null, enum information, target, feasibility,
 * null), {@code errors} (array of objects with the required keys slot_name, code, message) and {@code params} (the
 * caller-provided schema embedded verbatim, wrapped as an object when it lacks a type), with no additional properties
 * allowed.
 *
 * <p>This interface is the validation-package seam for the schema builder of the generation package: the orchestrator
 * builder wires the generation-package implementation to it, for example via the method reference
 * {@code negotiationJsonSchemaBuilder::buildSemanticValidationSchema}.
 *
 * @since 2026-06
 */
@FunctionalInterface
public interface SemanticSchemaBuilder {

    /**
     * Merges the caller-provided parameter schema into the semantic validation output contract.
     *
     * @param callerSchema caller-provided parameter JSON schema
     * @return merged JSON schema for the semantic validation structured LLM call
     */
    Map<String, Object> buildSemanticValidationSchema(Map<String, Object> callerSchema);
}
