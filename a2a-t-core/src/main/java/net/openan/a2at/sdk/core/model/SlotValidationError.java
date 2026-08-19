package net.openan.a2at.sdk.core.model;

/**
 * Structured validation error details for one named slot.
 *
 * <p>Instances are carried inside parameter-extraction failures so callers can inspect which slot failed, under which
 * error code, and why, without parsing exception messages.
 *
 * @param slotName name of the slot the error refers to
 * @param code machine-readable error code for the error
 * @param message human-readable explanation of the error
 * @since 2026-06
 */
public record SlotValidationError(String slotName, String code, String message) {}
