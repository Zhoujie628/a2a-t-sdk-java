package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthzScenarioLoaderTest {

    @Test
    void should_loadAndValidateAllScenarios() {
        List<AuthzScenario> scenarios = AuthzScenarioLoader.load("sample/authz-policy/scenarios.json");

        assertEquals(15, scenarios.size());
        // 预期成功在前（index 0-8），预期拒绝在中（index 9-13），客户端拦截在末尾（index 14）
        assertEquals("c1-nl-add-01", scenarios.get(0).label());
        assertEquals("from_text", scenarios.get(0).entry());
        assertEquals("success", scenarios.get(0).expected().server().outcome());
        assertEquals("c2-data-multi-07", scenarios.get(3).label());
        assertEquals("from_data_with_schema", scenarios.get(3).entry());
        assertEquals("success", scenarios.get(3).expected().server().outcome());
        assertEquals("b6-schema-variant-01", scenarios.get(7).label());
        assertEquals("success", scenarios.get(7).expected().server().outcome());
        assertEquals(null, scenarios.get(7).validateSchema());
        assertEquals("b6-schema-variant-02", scenarios.get(13).label());
        assertEquals(false, scenarios.get(13).validateSchema() == null);
        assertEquals("b1-nl-missing-01", scenarios.get(9).label());
        assertEquals("validation_semantic_rejected", scenarios.get(9).expected().server().outcome());
        assertEquals("c6-nl-mixed-07", scenarios.get(12).label());
        assertEquals("validation_semantic_rejected", scenarios.get(12).expected().server().outcome());
        assertEquals("a-nl-neg-01", scenarios.get(14).label());
        assertEquals("slot_validation_error", scenarios.get(14).expected().client().outcome());
        assertEquals(null, scenarios.get(14).expected().server());
    }

    @Test
    void should_throwIllegalStateException_WhenResourceNotFound() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> AuthzScenarioLoader.load("sample/authz-policy/nonexistent.json"));

        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void should_throwIllegalStateException_WhenJsonMalformed() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> AuthzScenarioLoader.load("sample/authz-policy/test/malformed.json"));

        assertTrue(ex.getMessage().contains("malformed") || ex.getMessage().contains("parse"));
    }

    @Test
    void should_throwIllegalArgumentException_WhenScenarioHasInvalidEntry() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AuthzScenarioLoader.load("sample/authz-policy/test/invalid-scenarios.json"));

        assertTrue(ex.getMessage().contains("bad-scenario"));
    }

    @Test
    void should_throwIllegalStateException_WhenScenariosIsNotAnArray() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> AuthzScenarioLoader.load("sample/authz-policy/test/scenarios-as-string.json"));

        assertTrue(ex.getMessage().contains("scenarios"));
    }

    @Test
    void should_throwIllegalStateException_WhenScenarioLabelIsNotAString() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> AuthzScenarioLoader.load("sample/authz-policy/test/scenario-label-non-string.json"));

        assertTrue(ex.getMessage().contains("label"));
    }

    @Test
    void should_parseValidateSchema_WhenPresent() {
        List<AuthzScenario> scenarios =
                AuthzScenarioLoader.load("sample/authz-policy/test/validate-schema-scenarios.json");

        assertEquals(2, scenarios.size());
        AuthzScenario present = scenarios.get(0);
        assertEquals("vs-present", present.label());
        assertNotNull(present.validateSchema());
        assertEquals(
                "应为简短的处置动作短语",
                ((Map<?, ?>) present.validateSchema().get("动网操作的授权策略列表")).get("处置类型"));
    }

    @Test
    void should_haveNullValidateSchema_WhenAbsent() {
        List<AuthzScenario> scenarios =
                AuthzScenarioLoader.load("sample/authz-policy/test/validate-schema-scenarios.json");

        assertEquals("vs-missing", scenarios.get(1).label());
        assertNull(scenarios.get(1).validateSchema());
    }

    @Test
    void should_throwIllegalStateException_WhenValidateSchemaIsEmpty() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> AuthzScenarioLoader.load("sample/authz-policy/test/validate-schema-empty.json"));

        assertTrue(ex.getMessage().contains("validate_schema"));
    }
}
