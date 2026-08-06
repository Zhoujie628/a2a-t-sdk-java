package net.openan.a2at.sdk.negotiation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.negotiation.handler.ClarificationNegotiation;
import net.openan.a2at.sdk.negotiation.handler.InformationNegotiation;
import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationHandlerBuilderTest {

    @Test
    void builderCreatesHandlerWithStoreAndMultipleNegotiationTypes() {
        NegotiationHandler handler = NegotiationHandler.builder()
                .store(new InMemoryNegotiationStore())
                .register(NegotiationType.CLARIFICATION, new ClarificationNegotiation())
                .register(NegotiationType.INFORMATION, new InformationNegotiation())
                .build();

        Map<String, Object> startPayload = handler.start(NegotiationType.INFORMATION, "latest prompt", Map.of());
        Map<String, Object> context = castStringObjectMap(startPayload.get(NegotiationHandler.NEGOTIATION_T_URI_NL));
        Map<String, Object> result = handler.receive("latest prompt", context);

        assertTrue(booleanValue(result.get("needResponse")));
        assertEquals("latest prompt", result.get("message"));
    }

    private static Map<String, Object> castStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            fail("Expected value to be a map.");
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> {
            if (!(key instanceof String stringKey)) {
                fail("Expected map key to be a string.");
                return;
            }
            result.put(stringKey, mapValue);
        });
        return result;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        fail("Expected value to be a boolean.");
        return false;
    }
}
