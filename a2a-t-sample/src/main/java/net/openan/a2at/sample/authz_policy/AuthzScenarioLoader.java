package net.openan.a2at.sample.authz_policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AuthzScenarioLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AuthzScenarioLoader() {}

    public static List<AuthzScenario> load(String resourcePath) {
        Map<String, Object> root;
        try (InputStream stream = AuthzScenarioLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Scenario resource not found: " + resourcePath);
            }
            root = MAPPER.readValue(stream, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to parse scenario resource: " + resourcePath + " - " + e.getMessage(), e);
        }

        Object rawScenariosObj = root.get("scenarios");
        if (rawScenariosObj == null) {
            throw new IllegalStateException("Missing 'scenarios' key in: " + resourcePath);
        }
        if (!(rawScenariosObj instanceof List<?> rawList)) {
            throw new IllegalStateException("'scenarios' must be an array in: " + resourcePath);
        }

        List<AuthzScenario> scenarios = rawList.stream()
                .map(item -> {
                    if (!(item instanceof Map<?, ?> raw)) {
                        throw new IllegalStateException("Scenario entry must be an object in: " + resourcePath);
                    }
                    Object labelObj = raw.get("label");
                    if (!(labelObj instanceof String label)) {
                        throw new IllegalStateException("Scenario 'label' must be a string in: " + resourcePath);
                    }
                    Object entryObj = raw.get("entry");
                    if (!(entryObj instanceof String entry)) {
                        throw new IllegalStateException("Scenario 'entry' must be a string in: " + resourcePath);
                    }
                    Object inputObj = raw.get("input");
                    if (!(inputObj instanceof Map<?, ?> input)) {
                        throw new IllegalStateException("Scenario 'input' must be an object in: " + resourcePath);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> inputMap = (Map<String, Object>) input;
                    Object expectedObj = raw.get("expected");
                    if (!(expectedObj instanceof Map<?, ?> expectedRaw)) {
                        throw new IllegalStateException("Scenario 'expected' must be an object in: " + resourcePath);
                    }
                    Object outcomeObj = expectedRaw.get("outcome");
                    if (!(outcomeObj instanceof String outcome)) {
                        throw new IllegalStateException(
                                "Scenario 'expected.outcome' must be a string in: " + resourcePath);
                    }
                    Map<String, Object> filledParams = null;
                    Object filledParamsObj = expectedRaw.get("filled_params");
                    if (filledParamsObj instanceof Map<?, ?> filledRaw) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> filledMap = (Map<String, Object>) filledRaw;
                        filledParams = filledMap;
                    }
                    String promptText = null;
                    Object promptTextObj = expectedRaw.get("prompt_text");
                    if (promptTextObj instanceof String pt) {
                        promptText = pt;
                    }
                    List<AuthzScenario.SlotErrorExpectation> slotErrors = parseSlotErrors(expectedRaw.get("slot_errors"));
                    AuthzScenario.AuthzExpected expected =
                            new AuthzScenario.AuthzExpected(outcome, filledParams, promptText, slotErrors);
                    return new AuthzScenario(label, entry, inputMap, expected);
                })
                .toList();

        for (AuthzScenario scenario : scenarios) {
            try {
                AuthzScenario.validate(scenario);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid scenario '" + scenario.label() + "': " + e.getMessage(), e);
            }
        }

        return scenarios;
    }

    private static List<AuthzScenario.SlotErrorExpectation> parseSlotErrors(Object raw) {
        if (!(raw instanceof List<?> rawList)) {
            return null;
        }
        List<AuthzScenario.SlotErrorExpectation> result = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Object slotNameObj = rawMap.get("slot_name");
            Object codeObj = rawMap.get("code");
            if (!(slotNameObj instanceof String slotName) || !(codeObj instanceof String code)) {
                continue;
            }
            result.add(new AuthzScenario.SlotErrorExpectation(slotName, code));
        }
        return result;
    }
}
