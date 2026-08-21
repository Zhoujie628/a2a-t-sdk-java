package net.openan.a2at.sdk.core.model;

import java.util.Map;

/**
 * Successful result of validating content and extracting parameters from it.
 *
 * @param data parameters validated against and extracted from the content, filled per the caller-provided schema
 * @since 2026-08
 */
public record FilledParamData(Map<String, Object> data) {}