package net.openan.a2at.sdk.corpus;

import org.jspecify.annotations.Nullable;

/**
 * The six scripted LLM failure markers of a corpus script step.
 *
 * <p>A {@code $fail} marker replaces one scripted LLM answer with a failure the later scripted client replays:
 * infrastructure failures, degenerate responses or the assertion failure that proves a zero-call run. The marker
 * names are part of the corpus format contract.
 *
 * @since 2026-08
 */
public enum LlmFailMarker {

    /** Throws a runtime (infrastructure) exception on the call. */
    RUNTIME_EXCEPTION("runtime-exception"),

    /** Answers an LLM error response on the call. */
    LLM_ERROR("llm-error"),

    /** Returns a null response on the call. */
    NULL_RESPONSE("null-response"),

    /** Returns a response with a blank content payload on the call. */
    BLANK_CONTENT("blank-content"),

    /** Returns a response whose content is not parseable JSON on the call. */
    NON_JSON("non-json"),

    /** Throws an assertion failure; the zero-call proof of the from-data and differential runs. */
    ASSERTION("assertion");

    private final String jsonName;

    LlmFailMarker(String jsonName) {
        this.jsonName = jsonName;
    }

    /**
     * Returns the corpus JSON name of this marker.
     *
     * @return marker name such as {@code non-json}
     */
    public String jsonName() {
        return jsonName;
    }

    /**
     * Resolves a corpus JSON name into a failure marker.
     *
     * @param jsonName corpus JSON name such as {@code non-json}
     * @return the marker, or null when the name matches none of the six markers
     */
    public static @Nullable LlmFailMarker fromJsonName(@Nullable String jsonName) {
        for (LlmFailMarker marker : values()) {
            if (marker.jsonName.equals(jsonName)) {
                return marker;
            }
        }
        return null;
    }
}
