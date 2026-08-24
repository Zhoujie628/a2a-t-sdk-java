package net.openan.a2at.sdk.negotiation.testdata;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The scripted LLM behavior of one corpus record.
 *
 * @param maxAttempts builder-level retry limit, or null for the builder default; clamping assertions stay in the
 *     handwritten suites
 * @param steps scripted answers consumed strictly step by step; an exhausted script fails the run instead of
 *     repeating the last answer
 * @since 2026-08
 */
public record LlmScript(@Nullable Integer maxAttempts, List<LlmScriptStep> steps) {

    public LlmScript {
        steps = List.copyOf(steps);
    }
}
