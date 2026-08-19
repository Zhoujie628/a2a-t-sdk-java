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
}
