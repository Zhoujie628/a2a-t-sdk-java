package net.openan.a2at.sdk.core.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link A2ATError}.
 *
 * <p>Tests cover the following scenarios:
 *
 * <ul>
 *   <li>Default error code when no explicit code is provided
 *   <li>Explicit error code forwarding
 *   <li>Null safety for the explicit code
 * </ul>
 *
 * @since 2026-08
 */
class A2ATErrorTest {

    /**
     * Verifies that {@link A2ATError#A2ATError(String)} and {@link A2ATError#A2ATError(String, Throwable)} fall back to
     * {@link A2ATErrorCodes#SDK_INTERNAL_ERROR}.
     *
     * <p>Scenario: Create A2A-T errors without an explicit code. Expected result: getCode() returns
     * {@code sdk_internal_error} and is never null.
     */
    @Test
    void should_defaultToSdkInternalErrorCode_When_createdWithoutExplicitCode() {
        assertEquals(A2ATErrorCodes.SDK_INTERNAL_ERROR, new A2ATError("boom").getCode());
        assertEquals(
                A2ATErrorCodes.SDK_INTERNAL_ERROR,
                new A2ATError("boom", new IllegalStateException("root")).getCode());
    }

    /**
     * Verifies that {@link A2ATError#A2ATError(String, String)} and {@link A2ATError#A2ATError(String, String,
     * Throwable)} carry the explicitly provided code.
     *
     * <p>Scenario: Create A2A-T errors with an explicit code. Expected result: getCode() returns the explicit code,
     * getMessage() returns the message and getCause() returns the cause when provided.
     */
    @Test
    void should_carryExplicitCode_When_createdWithExplicitCode() {
        assertEquals(
                A2ATErrorCodes.TEMPLATE_NOT_FOUND,
                new A2ATError(A2ATErrorCodes.TEMPLATE_NOT_FOUND, "missing").getCode());
        assertEquals("missing", new A2ATError(A2ATErrorCodes.TEMPLATE_NOT_FOUND, "missing").getMessage());
        assertNull(new A2ATError(A2ATErrorCodes.TEMPLATE_NOT_FOUND, "missing").getCause());

        IllegalStateException cause = new IllegalStateException("root");
        A2ATError withCause = new A2ATError(A2ATErrorCodes.TEMPLATE_NOT_FOUND, "missing", cause);
        assertEquals(A2ATErrorCodes.TEMPLATE_NOT_FOUND, withCause.getCode());
        assertEquals(cause, withCause.getCause());
    }

    /**
     * Verifies that the explicit-code constructors reject null codes.
     *
     * <p>Scenario: Attempt to create A2A-T errors with a null code. Expected result: NullPointerException is thrown.
     */
    @Test
    void should_throwNullPointerException_When_createdWithNullCode() {
        assertThrows(NullPointerException.class, () -> new A2ATError(null, "missing"));
        assertThrows(NullPointerException.class, () -> new A2ATError(null, "missing", new IllegalStateException()));
    }
}
