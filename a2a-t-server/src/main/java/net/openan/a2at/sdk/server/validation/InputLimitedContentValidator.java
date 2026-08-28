package net.openan.a2at.sdk.server.validation;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.InputLimitConfig;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.ContentValidator;

/**
 * Free-text input gate wrapped around one delegated content validator.
 *
 * <p>The gate rejects an oversized prompt with the code {@code input_text_too_long} before the delegated pipeline
 * starts, so no LLM call is made for an input that could not fit the LLM context anyway. The limit is configured
 * through {@code A2AT_INPUT_TEXT_MAX_CHARS} and defaults to {@link InputLimitConfig#DEFAULT_MAX_TEXT_CHARS}
 * characters.
 *
 * @since 2026-08
 */
public final class InputLimitedContentValidator implements ContentValidator {

    private final ContentValidator delegate;

    private final int maxTextChars;

    /**
     * Creates one gating validator around the given delegate.
     *
     * @param delegate content validator carrying the actual validation pipeline
     * @param maxTextChars maximum length in characters accepted for the prompt text
     */
    public InputLimitedContentValidator(ContentValidator delegate, int maxTextChars) {
        this.delegate = delegate;
        this.maxTextChars = maxTextChars;
    }

    @Override
    public FilledParamData validate(String prompt, Map<String, Object> schema, TemplateUri templateUri) {
        if (InputLimitConfig.isTooLong(prompt, maxTextChars)) {
            throw new ContentValidationException(
                    A2ATErrorCodes.INPUT_TEXT_TOO_LONG, InputLimitConfig.violationMessage(prompt, maxTextChars));
        }
        return delegate.validate(prompt, schema, templateUri);
    }
}
