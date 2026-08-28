package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InputLimitConfig} parsing and length-guard helpers.
 *
 * @since 2026-08
 */
class InputLimitConfigTest {

    @Test
    void fromMapFallsBackToDefaultWhenKeyAbsent() {
        InputLimitConfig config = InputLimitConfig.fromMap(Map.of());

        assertEquals(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS, config.maxTextChars());
        assertEquals(12288, config.maxTextChars());
    }

    @Test
    void fromMapParsesConfiguredValue() {
        InputLimitConfig config =
                InputLimitConfig.fromMap(Map.of(A2ATConfigKeys.Input.MAX_TEXT_CHARS, " 2048 "));

        assertEquals(2048, config.maxTextChars());
    }

    @Test
    void fromMapFallsBackToDefaultOnNonNumericValue() {
        InputLimitConfig config =
                InputLimitConfig.fromMap(Map.of(A2ATConfigKeys.Input.MAX_TEXT_CHARS, "not-a-number"));

        assertEquals(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS, config.maxTextChars());
    }

    @Test
    void fromMapFallsBackToDefaultOnNonPositiveValue() {
        InputLimitConfig config =
                InputLimitConfig.fromMap(Map.of(A2ATConfigKeys.Input.MAX_TEXT_CHARS, "0"));

        assertEquals(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS, config.maxTextChars());
    }

    @Test
    void fromMapFallsBackToDefaultOnBlankValue() {
        InputLimitConfig config = InputLimitConfig.fromMap(Map.of(A2ATConfigKeys.Input.MAX_TEXT_CHARS, "  "));

        assertEquals(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS, config.maxTextChars());
    }

    @Test
    void constructorRejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> new InputLimitConfig(0));
        assertThrows(IllegalArgumentException.class, () -> new InputLimitConfig(-1));
    }

    @Test
    void isTooLongComparesByLengthAgainstLimit() {
        assertTrue(InputLimitConfig.isTooLong("a".repeat(101), 100));
        assertFalse(InputLimitConfig.isTooLong("a".repeat(100), 100));
        assertFalse(InputLimitConfig.isTooLong("", 100));
        assertFalse(InputLimitConfig.isTooLong(null, 100));
    }

    @Test
    void violationMessageStatesLengthLimitAndKey() {
        String message = InputLimitConfig.violationMessage("a".repeat(13000), 12288);

        assertTrue(message.contains("13000"));
        assertTrue(message.contains("12288"));
        assertTrue(message.contains(A2ATConfigKeys.Input.MAX_TEXT_CHARS));
    }
}
