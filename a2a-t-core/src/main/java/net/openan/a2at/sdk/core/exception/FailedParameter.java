package net.openan.a2at.sdk.core.exception;

/**
 * Describes a single failed required slot during slot validation.
 *
 * @param parameterName the name of the required slot that failed validation
 * @param reason the failure reason, e.g. {@code missing_required}
 * @since 2026-08
 */
public record FailedParameter(String parameterName, String reason) {}