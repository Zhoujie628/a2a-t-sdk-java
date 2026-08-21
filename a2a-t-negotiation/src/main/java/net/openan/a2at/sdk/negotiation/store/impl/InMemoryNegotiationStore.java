package net.openan.a2at.sdk.negotiation.store.impl;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.openan.a2at.sdk.negotiation.store.NegotiationStore;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;

/**
 * In-memory negotiation store for early SDK iterations.
 *
 * @since 2026-06
 */
public final class InMemoryNegotiationStore implements NegotiationStore {

    private final Map<String, NegotiationRecord> records = new ConcurrentHashMap<>();

    @Override
    public void save(NegotiationRecord record) {
        Objects.requireNonNull(record, "Negotiation record must not be null.");
        Objects.requireNonNull(record.context(), "Negotiation record context must not be null.");
        String negotiationId = record.context().negotiationId();
        if (negotiationId == null || negotiationId.isEmpty()) {
            throw new IllegalArgumentException("negotiation id is null or empty.");
        }
        records.put(negotiationId, record);
    }

    @Override
    public NegotiationRecord get(String negotiationId) {
        return records.get(negotiationId);
    }

    @Override
    public void delete(String negotiationId) {
        records.remove(negotiationId);
    }
}
