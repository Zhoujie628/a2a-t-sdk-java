package net.openan.a2at.sdk.negotiation.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.negotiation.resources.DefaultNegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.validation.DefaultNegotiationComplianceChecker;
import net.openan.a2at.sdk.negotiation.validation.NegotiationRuleCheckResult;
import org.junit.jupiter.api.Test;

/**
 * Verifies the immutability and purity contracts of the negotiation content layer under concurrent access.
 *
 * <p>A shared {@link NegotiationContext} is read by many threads that each derive the next round: the original object
 * must never change and every derived object must be identical. The rule checker is a pure function: repeated concurrent calls with the same input must return the same result.
 */
class NegotiationContextImmutabilityTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final int THREADS = 8;

    private static final int ITERATIONS = 200;

    @Test
    void sharedContextNextRoundNeverMutatesTheOriginal() throws Exception {
        NegotiationContext shared = new NegotiationContext(UUID, 2, 5);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            Set<NegotiationContext> derived = Collections.synchronizedSet(new HashSet<>());
            List<Future<Void>> futures = new ArrayList<>();
            for (int index = 0; index < THREADS; index++) {
                futures.add(pool.submit(() -> {
                    for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                        derived.add(shared.nextRound());
                        assertEquals(2, shared.round(), "original round must never change");
                        assertEquals(5, shared.maxRounds(), "original maxRounds must never change");
                        assertEquals(UUID, shared.id(), "original id must never change");
                    }
                    return null;
                }));
            }
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            assertEquals(1, derived.size(), "every derived context must be identical");
            assertEquals(new NegotiationContext(UUID, 3, 5), derived.iterator().next());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void ruleCheckerIsPureUnderConcurrentCalls() throws Exception {
        String prompt = "## 协商上下文\n- id: " + UUID + "\n- round: 1\n- maxRounds: 5";
        Vocabulary vocabulary = Vocabulary.forLanguage("zh-CN");
        DefaultNegotiationComplianceChecker checker = new DefaultNegotiationComplianceChecker();
        NegotiationRuleCheckResult baseline = checker.check(prompt, vocabulary);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int index = 0; index < THREADS; index++) {
                futures.add(pool.submit(() -> {
                    for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                        NegotiationRuleCheckResult result = checker.check(prompt, vocabulary);
                        assertEquals(baseline.passed(), result.passed());
                        assertEquals(baseline.isNegotiation(), result.isNegotiation());
                        assertEquals(baseline.errors(), result.errors());
                        assertEquals(baseline.context(), result.context());
                    }
                    return null;
                }));
            }
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
