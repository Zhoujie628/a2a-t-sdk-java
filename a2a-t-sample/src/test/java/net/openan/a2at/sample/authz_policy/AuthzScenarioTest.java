package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import net.openan.a2at.sample.authz_policy.AuthzScenario.AuthzExpected;
import org.junit.jupiter.api.Test;

class AuthzScenarioTest {

    private static AuthzExpected success() {
        return new AuthzExpected("success", null, null, null);
    }

    @Test
    void should_acceptValidFromTextScenario() {
        AuthzScenario scenario = new AuthzScenario("add-from-text", "from_text", Map.of("text", "test"), success());

        assertDoesNotThrow(() -> AuthzScenario.validate(scenario));
    }

    @Test
    void should_acceptValidFromDataWithSchemaScenario() {
        AuthzScenario scenario = new AuthzScenario(
                "add-from-data",
                "from_data_with_schema",
                Map.of("data", Map.of("k", "v"), "schema", Map.of("k", "description")),
                success());

        assertDoesNotThrow(() -> AuthzScenario.validate(scenario));
    }

    @Test
    void should_rejectInvalidEntry() {
        AuthzScenario scenario = new AuthzScenario("bad", "invalid_entry", Map.of("text", "test"), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("Invalid entry: invalid_entry", ex.getMessage());
    }

    @Test
    void should_rejectInvalidExpectedOutcome() {
        AuthzScenario scenario = new AuthzScenario(
                "bad", "from_text", Map.of("text", "test"), new AuthzExpected("invalid_outcome", null, null, null));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("Invalid expected outcome: invalid_outcome", ex.getMessage());
    }

    @Test
    void should_rejectNullExpectedOutcome() {
        AuthzScenario scenario = new AuthzScenario(
                "bad", "from_text", Map.of("text", "test"), new AuthzExpected(null, null, null, null));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("Invalid expected outcome: null", ex.getMessage());
    }

    @Test
    void should_acceptSlotValidationErrorOutcome() {
        AuthzScenario scenario = new AuthzScenario(
                "reject", "from_text", Map.of("text", "test"),
                new AuthzExpected("slot_validation_error", null, null, null));

        assertDoesNotThrow(() -> AuthzScenario.validate(scenario));
    }

    @Test
    void should_acceptSemanticRejectedOutcome() {
        AuthzScenario scenario = new AuthzScenario(
                "reject", "from_text", Map.of("text", "test"),
                new AuthzExpected("validation_semantic_rejected", null, null, null));

        assertDoesNotThrow(() -> AuthzScenario.validate(scenario));
    }

    @Test
    void should_rejectNullExpected() {
        AuthzScenario scenario = new AuthzScenario("bad", "from_text", Map.of("text", "test"), null);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("expected must not be null", ex.getMessage());
    }

    @Test
    void should_rejectFromTextMissingText() {
        AuthzScenario scenario = new AuthzScenario("bad", "from_text", Map.of(), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_text scenario requires a non-blank input.text", ex.getMessage());
    }

    @Test
    void should_rejectFromTextBlankText() {
        AuthzScenario scenario = new AuthzScenario("bad", "from_text", Map.of("text", "  "), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_text scenario requires a non-blank input.text", ex.getMessage());
    }

    @Test
    void should_rejectFromDataMissingData() {
        AuthzScenario scenario =
                new AuthzScenario("bad", "from_data_with_schema", Map.of("schema", Map.of("k", "d")), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_data_with_schema scenario requires a non-empty input.data", ex.getMessage());
    }

    @Test
    void should_rejectFromDataMissingSchema() {
        AuthzScenario scenario =
                new AuthzScenario("bad", "from_data_with_schema", Map.of("data", Map.of("k", "v")), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_data_with_schema scenario requires a non-empty input.schema", ex.getMessage());
    }

    @Test
    void should_rejectFromDataWithSchema_EmptyData() {
        AuthzScenario scenario = new AuthzScenario(
                "bad", "from_data_with_schema", Map.of("data", Map.of(), "schema", Map.of("k", "d")), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_data_with_schema scenario requires a non-empty input.data", ex.getMessage());
    }

    @Test
    void should_rejectFromDataWithSchema_EmptySchema() {
        AuthzScenario scenario = new AuthzScenario(
                "bad", "from_data_with_schema", Map.of("data", Map.of("k", "v"), "schema", Map.of()), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_data_with_schema scenario requires a non-empty input.schema", ex.getMessage());
    }

    @Test
    void should_rejectNullInput() {
        AuthzScenario scenario = new AuthzScenario("bad", "from_text", null, success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("input must not be null", ex.getMessage());
    }

    @Test
    void should_rejectNullScenario() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(null));
        assertEquals("scenario must not be null", ex.getMessage());
    }
}
