package net.openan.a2at.sdk.negotiation.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class InMemoryNegotiationStoreTest {

    @Test
    void storeSavesGetsAndDeletesRecords() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationRecord record = new NegotiationRecord(
                new NegotiationContext(NegotiationType.TARGET, "neg-store", 1, NegotiationStatus.IN_PROGRESS),
                "message");

        store.save(record);

        assertEquals(record, store.get("neg-store"));

        store.delete("neg-store");

        assertNull(store.get("neg-store"));
    }

    @Test
    void saveRejectsNullRecordAndNullContext() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();

        assertEquals(
                "Negotiation record must not be null.",
                assertThrows(NullPointerException.class, () -> store.save(null)).getMessage());
        assertEquals(
                "Negotiation record context must not be null.",
                assertThrows(NullPointerException.class, () -> store.save(new NegotiationRecord(null, "message")))
                        .getMessage());
    }

    @Test
    void saveRejectsMissingNegotiationId() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();

        IllegalArgumentException missingId = assertThrows(
                IllegalArgumentException.class,
                () -> store.save(new NegotiationRecord(
                        new NegotiationContext(NegotiationType.TARGET, null, 1, NegotiationStatus.IN_PROGRESS),
                        "message")));

        assertEquals("negotiation id is null or empty.", missingId.getMessage());

        IllegalArgumentException emptyId = assertThrows(
                IllegalArgumentException.class,
                () -> store.save(new NegotiationRecord(
                        new NegotiationContext(NegotiationType.TARGET, "", 1, NegotiationStatus.IN_PROGRESS),
                        "message")));

        assertEquals("negotiation id is null or empty.", emptyId.getMessage());
    }
}
