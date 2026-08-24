package net.openan.a2at.sdk.corpus;

/**
 * One scripted answer of an LLM script.
 *
 * <p>After loader resolution every step is either a literal payload text (an inline corpus string or a resolved
 * {@code responses/} reference) or a failure marker. The tri-state of the corpus JSON — {@code $ref} object,
 * {@code $fail} object, literal string — collapses into exactly these two shapes.
 *
 * @since 2026-08
 */
public sealed interface LlmScriptStep {

    /**
     * A scripted answer carrying a literal payload text.
     *
     * @param json payload text handed to the scripted LLM client
     */
    record Payload(String json) implements LlmScriptStep {}

    /**
     * A scripted answer that fails with the given marker.
     *
     * @param marker failure marker the scripted LLM client replays
     */
    record Fail(LlmFailMarker marker) implements LlmScriptStep {}
}
