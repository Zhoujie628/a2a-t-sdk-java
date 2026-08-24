package net.openan.a2at.sdk.negotiation.testdata;

import org.jspecify.annotations.Nullable;

/**
 * Registry of the behavior contract names a corpus expectation can reference (design document §8.1, Q18).
 *
 * <p>The registry defines all twelve contracts at once with their P0/P1 level, so it doubles as the readable expectation
 * list of the corpus-generation workflow. The engine implements the four P0 contracts; referencing a P1 contract is an
 * explicit engine failure ("not yet lit") rather than a silent pass, so a corpus author always knows what is actually
 * asserted.
 *
 * @since 2026-08
 */
public enum Contract {

    /** Accept/Reject/Abort messages must carry the corresponding conclusion literal. P0, case-level. */
    CONCLUSION_LITERAL_PRESENT("conclusionLiteralPresent", true),

    /** The validate merge result must always carry the context keys id, round and maxRounds. P0, case-level. */
    CONTEXT_KEYS_IN_MERGED_PARAMS("contextKeysInMergedParams", true),

    /** Raw LLM failure details must never reach a user-visible message. P0, case-level. */
    NO_LLM_LEAK_IN_USER_MESSAGE("noLlmLeakInUserMessage", true),

    /** buildMetadataContent() always carries exactly the three negotiation metadata entries. P0, case-level. */
    METADATA_TRIPLE_SHAPE("metadataTripleShape", true),

    /** The rendered output must not contain an unreplaced {@code {{}} slot. P1, case-level. */
    NO_RENDER_SLOT_LEAK("noRenderSlotLeak", false),

    /** The rendered text must not contain the context id (41d0247 invariant). P1, case-level. */
    NO_CONTEXT_IN_RENDERED_TEXT("noContextInRenderedText", false),

    /** The returned template URI must equal the input template URI. P1, case-level. */
    TEMPLATE_URI_ECHO("templateUriEcho", false),

    /** An error message must contain its error code literal. P1, case-level. */
    ERROR_CODE_IN_MESSAGE("errorCodeInMessage", false),

    /** A rule-gate failure must happen with zero LLM calls. P1, case-level. */
    ZERO_LLM_CALLS_ON_RULE_GATE("zeroLlmCallsOnRuleGate", false),

    /** The same input must always fail with the same error code. P1, case-level. */
    ERROR_CODE_DETERMINISM("errorCodeDeterminism", false),

    /** Within one scenario the round must only increase. P1, scenario-level. */
    ROUND_MONOTONIC("roundMonotonic", false),

    /** nextRound() must not modify the original context. P1, property-level. */
    CONTEXT_IMMUTABILITY("contextImmutability", false);

    private final String jsonName;

    private final boolean p0;

    Contract(String jsonName, boolean p0) {
        this.jsonName = jsonName;
        this.p0 = p0;
    }

    /**
     * Returns the corpus JSON name of this contract.
     *
     * @return contract name such as {@code conclusionLiteralPresent}
     */
    public String jsonName() {
        return jsonName;
    }

    /**
     * Returns whether this contract is a P0 contract the engine asserts today.
     *
     * @return true for the four implemented P0 contracts
     */
    public boolean isP0() {
        return p0;
    }

    /**
     * Resolves a corpus JSON name into a contract.
     *
     * @param jsonName corpus JSON name such as {@code metadataTripleShape}
     * @return the contract, or null when the name matches none of the twelve registered contracts
     */
    public static @Nullable Contract fromJsonName(@Nullable String jsonName) {
        for (Contract contract : values()) {
            if (contract.jsonName.equals(jsonName)) {
                return contract;
            }
        }
        return null;
    }
}
