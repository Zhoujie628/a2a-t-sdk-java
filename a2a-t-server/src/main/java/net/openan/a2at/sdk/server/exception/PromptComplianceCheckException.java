package net.openan.a2at.sdk.server.exception;

import net.openan.a2at.sdk.core.exception.A2ATError;

/**
 * Compliance-check failure carrying a stable error code and the compliance stage where the failure occurred.
 *
 * <p>Part of the {@link A2ATError} tree: callers can catch the {@link A2ATError} root to handle any A2A-T processing
 * failure and dispatch on its machine-readable error code.
 *
 * @since 2026-06
 */
public final class PromptComplianceCheckException extends A2ATError {

    private final String stage;

    /**
     * Creates a standardized compliance-check exception.
     *
     * @param code stable error code
     * @param message human-readable failure message
     * @param stage compliance stage where the failure occurred
     */
    public PromptComplianceCheckException(String code, String message, String stage) {
        super(code, message);
        this.stage = stage;
    }

    /**
     * Returns the compliance stage where the failure occurred.
     *
     * @return failure stage
     */
    public String getStage() {
        return stage;
    }
}
