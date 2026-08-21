package net.openan.a2at.sdk.core.validation;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.SlotValidationError;

/**
 * Outcome of a semantic validation step in the content validation pipeline.
 *
 * @param verdict overall semantic verdict; {@code true} only when every semantic constraint holds
 * @param errors structured semantic errors; empty when the verdict is true
 * @param params parameters extracted from the content per the caller-provided schema
 * @since 2026-08
 */
public record ValidationResult(boolean verdict, List<SlotValidationError> errors, Map<String, Object> params) {}