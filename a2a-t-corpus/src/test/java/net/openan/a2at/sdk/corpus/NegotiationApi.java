package net.openan.a2at.sdk.corpus;

import org.jspecify.annotations.Nullable;

/**
 * The fifteen APIs the corpus exercises: the twelve {@code NegotiationContentService} APIs plus the three task-API
 * facade methods of the closed loop ({@code generateTaskPromptFromText}, {@code generateTaskPromptFromDataWithSchema}
 * and {@code validateTaskPromptAndDataFilling}).
 *
 * <p>The corpus references APIs by their Java method name; the later case engine dispatches on this enum, so a
 * misspelled API name fails at corpus load time instead of silently skipping a case.
 *
 * @since 2026-08
 */
public enum NegotiationApi {

    /** {@code generateProposeFromText}: free text to a propose message through one LLM extraction. */
    GENERATE_PROPOSE_FROM_TEXT("generateProposeFromText", Family.FROM_TEXT),

    /** {@code generateAcceptFromText}: free text to an accept message through one LLM extraction. */
    GENERATE_ACCEPT_FROM_TEXT("generateAcceptFromText", Family.FROM_TEXT),

    /** {@code generateRejectFromText}: free text to a reject message through one LLM extraction. */
    GENERATE_REJECT_FROM_TEXT("generateRejectFromText", Family.FROM_TEXT),

    /** {@code generateAbortFromText}: free text to an abort message through one LLM extraction. */
    GENERATE_ABORT_FROM_TEXT("generateAbortFromText", Family.FROM_TEXT),

    /** {@code generateProposeFromData}: typed data to a propose message, deterministically. */
    GENERATE_PROPOSE_FROM_DATA("generateProposeFromData", Family.FROM_DATA),

    /** {@code generateAcceptFromData}: typed data to an accept message, deterministically. */
    GENERATE_ACCEPT_FROM_DATA("generateAcceptFromData", Family.FROM_DATA),

    /** {@code generateRejectFromData}: typed data to a reject message, deterministically. */
    GENERATE_REJECT_FROM_DATA("generateRejectFromData", Family.FROM_DATA),

    /** {@code generateAbortFromData}: typed data to an abort message, deterministically. */
    GENERATE_ABORT_FROM_DATA("generateAbortFromData", Family.FROM_DATA),

    /** {@code validateProposePromptAndDataFilling}: rule gate plus semantic validation of a propose message. */
    VALIDATE_PROPOSE_PROMPT_AND_DATA_FILLING("validateProposePromptAndDataFilling", Family.VALIDATE),

    /** {@code validateAcceptPromptAndDataFilling}: rule gate plus semantic validation of an accept message. */
    VALIDATE_ACCEPT_PROMPT_AND_DATA_FILLING("validateAcceptPromptAndDataFilling", Family.VALIDATE),

    /** {@code validateRejectPromptAndDataFilling}: rule gate plus semantic validation of a reject message. */
    VALIDATE_REJECT_PROMPT_AND_DATA_FILLING("validateRejectPromptAndDataFilling", Family.VALIDATE),

    /** {@code validateAbortPromptAndDataFilling}: rule gate plus semantic validation of an abort message. */
    VALIDATE_ABORT_PROMPT_AND_DATA_FILLING("validateAbortPromptAndDataFilling", Family.VALIDATE),

    /**
     * {@code generateTaskPromptFromText}: workbench-side task prompt generation from free text, one LLM slot-extraction
     * call (runs through the real {@code A2ATClient} facade builder assembly with the scripted LLM injected).
     */
    GENERATE_TASK_PROMPT_FROM_TEXT("generateTaskPromptFromText", Family.TASK),

    /**
     * {@code generateTaskPromptFromDataWithSchema}: workbench-side task prompt generation from structured input plus a
     * data schema, one LLM slot-extraction call.
     */
    GENERATE_TASK_PROMPT_FROM_DATA_WITH_SCHEMA("generateTaskPromptFromDataWithSchema", Family.TASK),

    /**
     * {@code validateTaskPromptAndDataFilling}: OMC-side validation of a received task prompt and extraction of its
     * filled parameters; a schema slot the prompt misses surfaces as a null-valued parameter.
     */
    VALIDATE_TASK_PROMPT_AND_DATA_FILLING("validateTaskPromptAndDataFilling", Family.TASK);

    private final String jsonName;

    private final Family family;

    NegotiationApi(String jsonName, Family family) {
        this.jsonName = jsonName;
        this.family = family;
    }

    /**
     * Returns the corpus JSON name of this API, which equals the {@code NegotiationContentService} method name.
     *
     * @return method name such as {@code generateAcceptFromText}
     */
    public String jsonName() {
        return jsonName;
    }

    /**
     * Returns the API family this API belongs to.
     *
     * @return from-text, from-data or validate family
     */
    public Family family() {
        return family;
    }

    /**
     * Resolves a corpus JSON name into an API.
     *
     * @param jsonName corpus JSON name such as {@code generateAcceptFromText}
     * @return the API, or null when the name matches none of the fifteen methods
     */
    public static @Nullable NegotiationApi fromJsonName(@Nullable String jsonName) {
        for (NegotiationApi api : values()) {
            if (api.jsonName.equals(jsonName)) {
                return api;
            }
        }
        return null;
    }

    /** The four API families of the negotiation content service plus the closed-loop task family. */
    public enum Family {

        /** The four from-text generation methods with exactly one LLM extraction call. */
        FROM_TEXT,

        /** The four from-data generation methods without any LLM call. */
        FROM_DATA,

        /** The four validation methods with a rule gate and one semantic LLM call. */
        VALIDATE,

        /**
         * The three closed-loop task APIs of the private-line complaint diagnosis: two task-prompt generation methods
         * with one LLM slot-extraction call each, and one task-prompt validation method with one LLM semantic call.
         */
        TASK
    }
}
