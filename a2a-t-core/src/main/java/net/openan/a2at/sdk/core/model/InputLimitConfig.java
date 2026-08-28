package net.openan.a2at.sdk.core.model;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Input limit configuration resolved from unified SDK config.
 *
 * <p>The limits guard every facade entry point that accepts a free-text {@link String} and forwards it to an LLM step,
 * so oversized inputs fail fast before any LLM call instead of overflowing the LLM context.
 *
 * @since 2026-08
 */
@Slf4j
public record InputLimitConfig(int maxTextChars) {

    /** Default maximum length in characters ({@link String#length()}) accepted for free-text inputs. */
    public static final int DEFAULT_MAX_TEXT_CHARS = 12288;

    private static final String NON_NUMERIC_OR_NON_POSITIVE_LOG_FORMAT =
            "Input max text chars value is not a valid positive integer, falling back to default. key={} raw_value={}"
                    + " default_value={}";

    public InputLimitConfig {
        if (maxTextChars <= 0) {
            throw new IllegalArgumentException("maxTextChars must be positive: " + maxTextChars);
        }
    }

    /**
     * Builds one input limit config from raw {@code .env} values.
     *
     * @param values raw config values
     * @return resolved input limit config
     */
    public static InputLimitConfig fromMap(Map<String, String> values) {
        String rawValue = values.get(A2ATConfigKeys.Input.MAX_TEXT_CHARS);
        if (StringUtils.isBlank(rawValue)) {
            return new InputLimitConfig(DEFAULT_MAX_TEXT_CHARS);
        }
        try {
            return new InputLimitConfig(Integer.parseInt(rawValue.trim()));
        } catch (IllegalArgumentException error) {
            log.atWarn().log(
                    NON_NUMERIC_OR_NON_POSITIVE_LOG_FORMAT,
                    A2ATConfigKeys.Input.MAX_TEXT_CHARS,
                    rawValue.trim(),
                    DEFAULT_MAX_TEXT_CHARS);
            return new InputLimitConfig(DEFAULT_MAX_TEXT_CHARS);
        }
    }

    /**
     * Reports whether the given free-text input exceeds the given character limit.
     *
     * @param text free-text input; {@code null} is never too long
     * @param maxTextChars maximum accepted length in characters
     * @return true when the input length exceeds the limit
     */
    public static boolean isTooLong(String text, int maxTextChars) {
        return text != null && text.length() > maxTextChars;
    }

    /**
     * Builds the violation message for an oversized free-text input.
     *
     * @param text oversized free-text input
     * @param maxTextChars maximum accepted length in characters
     * @return message stating the actual length, the limit and the configuring key
     */
    public static String violationMessage(String text, int maxTextChars) {
        return "input text length " + text.length() + " exceeds the configured maximum of " + maxTextChars
                + " characters (" + A2ATConfigKeys.Input.MAX_TEXT_CHARS + ")";
    }
}
