package net.openan.a2at.sdk.negotiation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import net.openan.a2at.sdk.negotiation.runtime.helper.NegotiationPayloadMapper;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationPayloadMapperTest {

    @Test
    void contextPayloadRoundTripsBetweenMapAndContext() {
        NegotiationContext context =
                new NegotiationContext(NegotiationType.TARGET, "neg-map", 2, NegotiationStatus.IN_PROGRESS);

        Map<String, Object> payload = NegotiationPayloadMapper.contextPayload(context);
        NegotiationContext rebuilt = NegotiationPayloadMapper.contextFromMap(payload);

        assertEquals(context, rebuilt);
    }

    @Test
    void payloadIncludesFactsWhenProvided() {
        NegotiationContext context =
                new NegotiationContext(NegotiationType.INFORMATION, "neg-payload", 1, NegotiationStatus.IN_PROGRESS);

        Map<String, Object> payload =
                NegotiationPayloadMapper.payload(context, "latest prompt", Map.of("source", "server"));

        @SuppressWarnings("unchecked")
        Map<String, Object> negotiationData = (Map<String, Object>) payload.get(NegotiationHandler.NEGOTIATION_T_URI_NL);
        assertEquals("latest prompt", negotiationData.get("message"));
        assertEquals("server", ((Map<?, ?>) payload.get("facts")).get("source"));
    }
}
