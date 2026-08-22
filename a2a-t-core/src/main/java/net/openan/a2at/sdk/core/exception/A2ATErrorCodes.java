package net.openan.a2at.sdk.core.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Centralized machine-readable error code constants shared across A2A-T SDK processing failures.
 *
 * @since 2026-06
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class A2ATErrorCodes {

    /** Default code for SDK processing failures without a more specific code. */
    public static final String SDK_INTERNAL_ERROR = "sdk_internal_error";

    /** Default code for parameter-extraction failures without a more specific code. */
    public static final String PARAM_EXTRACTION_FAILED = "param_extraction_failed";

    /** A referenced template could not be resolved. */
    public static final String TEMPLATE_NOT_FOUND = "template_not_found";

    /** Structured content could not be extracted from a free-text input. */
    public static final String NEGOTIATION_CONTENT_EXTRACT_FAILED = "negotiation_content_extract_failed";

    /** Semantic validation rejected the input. */
    public static final String NEGOTIATION_SEMANTIC_REJECTED = "negotiation_semantic_rejected";

    /** One or more structural rules of the expected template were violated. */
    public static final String NEGOTIATION_RULE_VIOLATION = "negotiation_rule_violation";

    /** A required slot is missing from the input. */
    public static final String NEGOTIATION_SLOT_MISSING = "negotiation_slot_missing";

    /** The input is not valid for the requested operation. */
    public static final String NEGOTIATION_INVALID_INPUT = "negotiation_invalid_input";

    /** An LLM infrastructure failure prevented the step from completing. */
    public static final String NEGOTIATION_LLM_INFRASTRUCTURE_ERROR = "negotiation_llm_infrastructure_error";

    /** The input is not valid for the requested validation operation. */
    public static final String VALIDATION_INVALID_INPUT = "validation_invalid_input";

    /** One or more structural rules of the expected content were violated. */
    public static final String VALIDATION_RULE_VIOLATION = "validation_rule_violation";

    /** Semantic validation rejected the content. */
    public static final String VALIDATION_SEMANTIC_REJECTED = "validation_semantic_rejected";

    /** An LLM infrastructure failure prevented the validation step from completing. */
    public static final String VALIDATION_LLM_INFRASTRUCTURE_ERROR = "validation_llm_infrastructure_error";

    /** A referenced prompt resource could not be resolved. */
    public static final String VALIDATION_PROMPT_RESOURCE_NOT_FOUND = "validation_prompt_resource_not_found";

    /** A prompt resource could not be loaded from its configured source. */
    public static final String PROMPT_RESOURCE_LOAD_ERROR = "prompt_resource_load_error";

    /** The slot schema describing the expected slots could not be resolved. */
    public static final String SLOT_SCHEMA_NOT_FOUND = "slot_schema_not_found";

    /** An LLM invocation failed at the infrastructure level. */
    public static final String LLM_INVOCATION_FAILED = "llm_invocation_failed";

    /** Rendering a template with its slot values failed. */
    public static final String RENDER_FAILED = "render_failed";

    /** The slot values extracted or provided for a template failed validation. */
    public static final String SLOT_VALIDATION_ERROR = "slot_validation_error";

    /** A processed prompt could not be parsed into its structured parts. */
    public static final String PROCESSED_PROMPT_PARSE_ERROR = "processed_prompt_parse_error";
}
