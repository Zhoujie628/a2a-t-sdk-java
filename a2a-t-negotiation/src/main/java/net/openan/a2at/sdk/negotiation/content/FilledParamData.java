package net.openan.a2at.sdk.negotiation.content;

import java.util.Map;

/**
 * Successful result of validating a negotiation message and extracting parameters from it.
 *
 * @param data parameters validated against and extracted from the message, filled per the caller-provided schema
 * @since 2026-06
 */
public record FilledParamData(Map<String, Object> data) {}
