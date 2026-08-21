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
 * <p>Example: {@code StandardTemplates.ENERGY_SAVING.uri()} is {@code Task-T/network-layer/energy-saving/v1}. The
 * Task-T and Notification-T templates carry the {@code network-layer} domain segment; Authorization-T and
 * Negotiation-T templates do not.
 *
 * @since 2026-08
 */
public final class StandardTemplates {

    private StandardTemplates() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** Extension name of the Task-T template family. */
    public static final String TASK_EXTENSION_NAME = "Task-T";

    /** Extension name of the Notification-T template family. */
    public static final String NOTIFICATION_EXTENSION_NAME = "Notification-T";

    /** Extension name of the Authorization-T template family. */
    public static final String AUTHORIZATION_EXTENSION_NAME = "Authorization-T";

    /** Extension name of the Negotiation-T template family. */
    public static final String NEGOTIATION_EXTENSION_NAME = "Negotiation-T";

    /** Domain segment carried by the Task-T and Notification-T template paths. */
    public static final String NETWORK_LAYER_SEGMENT = "network-layer";

    private static final String V1 = TemplateUri.DEFAULT_TEMPLATE_VERSION;

    /** Task-T template for the energy-saving scenario. */
    public static final TemplateUri ENERGY_SAVING =
            TemplateUri.of(TASK_EXTENSION_NAME, V1, NETWORK_LAYER_SEGMENT, "energy-saving");

    /** Task-T template for the private-line-complaint scenario. */
    public static final TemplateUri PRIVATE_LINE_COMPLAINT =
            TemplateUri.of(TASK_EXTENSION_NAME, V1, NETWORK_LAYER_SEGMENT, "private-line-complaint");

    /** Notification-T template for the subscribe-incident scenario. */
    public static final TemplateUri SUBSCRIBE_INCIDENT =
            TemplateUri.of(NOTIFICATION_EXTENSION_NAME, V1, NETWORK_LAYER_SEGMENT, "subscribe-incident");

    /** Notification-T template for the service-recovery scenario. */
    public static final TemplateUri SERVICE_RECOVERY =
            TemplateUri.of(NOTIFICATION_EXTENSION_NAME, V1, NETWORK_LAYER_SEGMENT, "service-recovery");

    /** Authorization-T template for the authorization-policy-management scenario. */
    public static final TemplateUri AUTHORIZATION_POLICY_MANAGEMENT =
            TemplateUri.of(AUTHORIZATION_EXTENSION_NAME, V1, "authorization-policy-management");

    /** Negotiation-T propose template for information negotiation. */
    public static final TemplateUri INFORMATION_NEGOTIATION_PROPOSE =
            TemplateUri.of(NEGOTIATION_EXTENSION_NAME, V1, "information-negotiation", "propose");

    /** Negotiation-T accept-reject template for information negotiation. */
    public static final TemplateUri INFORMATION_NEGOTIATION_ACCEPT_REJECT =
            TemplateUri.of(NEGOTIATION_EXTENSION_NAME, V1, "information-negotiation", "accept-reject");

    /** Negotiation-T propose template for target negotiation. */
    public static final TemplateUri TARGET_NEGOTIATION_PROPOSE =
            TemplateUri.of(NEGOTIATION_EXTENSION_NAME, V1, "target-negotiation", "propose");

    /** Negotiation-T accept-reject template for target negotiation. */
    public static final TemplateUri TARGET_NEGOTIATION_ACCEPT_REJECT =
            TemplateUri.of(NEGOTIATION_EXTENSION_NAME, V1, "target-negotiation", "accept-reject");

    /** Negotiation-T propose template for feasibility negotiation. */
    public static final TemplateUri FEASIBILITY_NEGOTIATION_PROPOSE =
            TemplateUri.of(NEGOTIATION_EXTENSION_NAME, V1, "feasibility-negotiation", "propose");

    /** Negotiation-T accept-reject template for feasibility negotiation. */
    public static final TemplateUri FEASIBILITY_NEGOTIATION_ACCEPT_REJECT =
            TemplateUri.of(NEGOTIATION_EXTENSION_NAME, V1, "feasibility-negotiation", "accept-reject");

    /** All built-in Task-T templates. */
    public static final List<TemplateUri> TASK = List.of(ENERGY_SAVING, PRIVATE_LINE_COMPLAINT);

    /** All built-in Notification-T templates. */
    public static final List<TemplateUri> NOTIFICATION = List.of(SUBSCRIBE_INCIDENT, SERVICE_RECOVERY);

    /** All built-in Authorization-T templates. */
    public static final List<TemplateUri> AUTHORIZATION = List.of(AUTHORIZATION_POLICY_MANAGEMENT);

    /** All built-in Negotiation-T templates. */
    public static final List<TemplateUri> NEGOTIATION = List.of(
            INFORMATION_NEGOTIATION_PROPOSE,
            INFORMATION_NEGOTIATION_ACCEPT_REJECT,
            TARGET_NEGOTIATION_PROPOSE,
            TARGET_NEGOTIATION_ACCEPT_REJECT,
            FEASIBILITY_NEGOTIATION_PROPOSE,
            FEASIBILITY_NEGOTIATION_ACCEPT_REJECT);
}
