package net.openan.a2at.sdk.negotiation.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NegotiationContextTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    @Test
    void constructionAcceptsLegalContext() {
        NegotiationContext context = new NegotiationContext(SESSION_ID, 1, 5);

        assertEquals(SESSION_ID, context.id());
        assertEquals(1, context.round());
        assertEquals(5, context.maxRounds());
    }

    @Test
    void blankIdThrowsWithDistinguishableMessage() {
        IllegalArgumentException blankException =
                assertThrows(IllegalArgumentException.class, () -> new NegotiationContext("", 1, 5));
        IllegalArgumentException whitespaceException =
                assertThrows(IllegalArgumentException.class, () -> new NegotiationContext("   ", 1, 5));
        IllegalArgumentException nullException =
                assertThrows(IllegalArgumentException.class, () -> new NegotiationContext(null, 1, 5));

        assertTrue(blankException.getMessage().contains("id"));
        assertTrue(blankException.getMessage().contains("blank"));
        assertEquals(blankException.getMessage(), whitespaceException.getMessage());
        assertEquals(blankException.getMessage(), nullException.getMessage());
    }

    @Test
    void zeroRoundThrowsWithDistinguishableMessage() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new NegotiationContext(SESSION_ID, 0, 5));

        assertTrue(exception.getMessage().contains("round"));
        assertTrue(exception.getMessage().contains("0"));
    }

    @Test
    void negativeRoundThrowsWithDistinguishableMessage() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new NegotiationContext(SESSION_ID, -1, 5));

        assertTrue(exception.getMessage().contains("round"));
        assertTrue(exception.getMessage().contains("-1"));
    }

    @Test
    void zeroMaxRoundsThrowsWithDistinguishableMessage() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new NegotiationContext(SESSION_ID, 1, 0));

        assertTrue(exception.getMessage().contains("maxRounds"));
        assertTrue(exception.getMessage().contains("0"));
    }

    @Test
    void roundGreaterThanMaxRoundsIsAllowedAtConstruction() {
        NegotiationContext context = new NegotiationContext(SESSION_ID, 6, 5);

        assertEquals(6, context.round());
        assertTrue(context.isExhausted());
    }

    @Test
    void nextRoundReturnsNewInstanceAndLeavesOriginalUnchanged() {
        NegotiationContext context = new NegotiationContext(SESSION_ID, 1, 5);
        NegotiationContext advanced = context.nextRound();

        assertEquals(2, advanced.round());
        assertEquals(SESSION_ID, advanced.id());
        assertEquals(5, advanced.maxRounds());
        assertEquals(1, context.round());
        assertNotEquals(context, advanced);
    }

    @Test
    void isExhaustedIsFalseAtBoundaryAndTrueBeyondIt() {
        assertFalse(new NegotiationContext(SESSION_ID, 4, 5).isExhausted());
        assertFalse(new NegotiationContext(SESSION_ID, 5, 5).isExhausted());
        assertTrue(new NegotiationContext(SESSION_ID, 6, 5).isExhausted());
    }

    @Test
    void factoryAppliesDefaultMaxRounds() {
        NegotiationContext context = NegotiationContext.of(SESSION_ID, 2);

        assertEquals(5, NegotiationContext.DEFAULT_MAX_ROUNDS);
        assertEquals(5, context.maxRounds());
        assertEquals(2, context.round());
        assertEquals(SESSION_ID, context.id());
    }
}
