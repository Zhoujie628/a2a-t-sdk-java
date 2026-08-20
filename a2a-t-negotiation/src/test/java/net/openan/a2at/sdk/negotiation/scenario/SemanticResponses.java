package net.openan.a2at.sdk.negotiation.scenario;

/**
 * Builders of the scripted semantic validation responses of the negotiation scenario tests.
 *
 * <p>Every response satisfies the four-key output contract of the semantic validation step: {@code semantic_verdict},
 * {@code negotiation_type}, {@code errors} and {@code params}.
 */
final class SemanticResponses {

    private SemanticResponses() {}

    /**
     * Builds a positive semantic validation response of one negotiation type.
     *
     * @param negotiationType negotiation type implied by the message, such as {@code information}
     * @param paramsJson JSON object literal carried as the extracted parameters
     * @return JSON response text
     */
    static String acceptance(String negotiationType, String paramsJson) {
        return "{\"semantic_verdict\":true,\"negotiation_type\":\"" + negotiationType + "\",\"errors\":[],\"params\":"
                + paramsJson + "}";
    }

    /**
     * Builds a negative semantic validation response carrying structured error details.
     *
     * @param errorsJson JSON array literal of error objects with slot_name, code and message
     * @return JSON response text
     */
    static String rejection(String errorsJson) {
        return "{\"semantic_verdict\":false,\"negotiation_type\":null,\"errors\":" + errorsJson + ",\"params\":{}}";
    }
}
