package net.openan.a2at.sdk.core.validation;

import java.util.List;

/**
 * Constants for the built-in content templates shipped with the SDK, in the spirit of
 * {@code java.nio.charset.StandardCharsets}.
 *
 * <p>Each constant is a language-neutral {@link TemplateUri}; the language is global prompt runtime context and is
 * bound by the SDK, not by the caller. Use these constants instead of hand-written URI strings to get compile-time
 * safety against spelling drift.
 *
 * <p>Example: {@code StandardTemplates.ENERGY_SAVING.uri()} is {@code Task-T/v1/energy-saving}.
 *
 * @since 2026-08
 */
public final class StandardTemplates {

    private StandardTemplates() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** Task-T template for the energy-saving scenario. */
    public static final TemplateUri ENERGY_SAVING = TemplateUri.of("Task-T", "v1", "energy-saving");

    /** Task-T template for the private-line-complaint scenario. */
    public static final TemplateUri PRIVATE_LINE_COMPLAINT =
            TemplateUri.of("Task-T", "v1", "private-line-complaint");

    /** Notification-T template for the subscribe-incident scenario. */
    public static final TemplateUri SUBSCRIBE_INCIDENT = TemplateUri.of("Notification-T", "v1", "subscribe-incident");

    /** Notification-T template for the service-recovery scenario. */
    public static final TemplateUri SERVICE_RECOVERY = TemplateUri.of("Notification-T", "v1", "service-recovery");

    /** Authorization-T template for the authz-policy-mgr scenario. */
    public static final TemplateUri AUTHZ_POLICY_MGR = TemplateUri.of("Authorization-T", "v1", "authz-policy-mgr");

    /** Negotiation-T propose template for information negotiation. */
    public static final TemplateUri INFORMATION_NEGOTIATION_PROPOSE =
            TemplateUri.of("Negotiation-T", "v1", "information-negotiation", "propose");

    /** Negotiation-T accept-reject template for information negotiation. */
    public static final TemplateUri INFORMATION_NEGOTIATION_ACCEPT_REJECT =
            TemplateUri.of("Negotiation-T", "v1", "information-negotiation", "accept-reject");

    /** Negotiation-T propose template for target negotiation. */
    public static final TemplateUri TARGET_NEGOTIATION_PROPOSE =
            TemplateUri.of("Negotiation-T", "v1", "target-negotiation", "propose");

    /** Negotiation-T accept-reject template for target negotiation. */
    public static final TemplateUri TARGET_NEGOTIATION_ACCEPT_REJECT =
            TemplateUri.of("Negotiation-T", "v1", "target-negotiation", "accept-reject");

    /** Negotiation-T propose template for feasibility negotiation. */
    public static final TemplateUri FEASIBILITY_NEGOTIATION_PROPOSE =
            TemplateUri.of("Negotiation-T", "v1", "feasibility-negotiation", "propose");

    /** Negotiation-T accept-reject template for feasibility negotiation. */
    public static final TemplateUri FEASIBILITY_NEGOTIATION_ACCEPT_REJECT =
            TemplateUri.of("Negotiation-T", "v1", "feasibility-negotiation", "accept-reject");

    /** All built-in Task-T templates. */
    public static final List<TemplateUri> TASK = List.of(ENERGY_SAVING, PRIVATE_LINE_COMPLAINT);

    /** All built-in Notification-T templates. */
    public static final List<TemplateUri> NOTIFICATION = List.of(SUBSCRIBE_INCIDENT, SERVICE_RECOVERY);

    /** All built-in Authorization-T templates. */
    public static final List<TemplateUri> AUTHORIZATION = List.of(AUTHZ_POLICY_MGR);

    /** All built-in Negotiation-T templates. */
    public static final List<TemplateUri> NEGOTIATION = List.of(
            INFORMATION_NEGOTIATION_PROPOSE,
            INFORMATION_NEGOTIATION_ACCEPT_REJECT,
            TARGET_NEGOTIATION_PROPOSE,
            TARGET_NEGOTIATION_ACCEPT_REJECT,
            FEASIBILITY_NEGOTIATION_PROPOSE,
            FEASIBILITY_NEGOTIATION_ACCEPT_REJECT);
}
