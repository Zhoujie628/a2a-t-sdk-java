package net.openan.a2at.sdk.corpus.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.generation.NegotiationContentService;
import net.openan.a2at.sdk.corpus.ScriptedNegotiationLlmClient;

/**
 * Large-parameter-space property (design §8.3): extraction and merge correctness for parameter maps of 50+ keys in
 * the shape of telecom network-element configuration negotiation.
 *
 * <p>The scripted semantic validator returns every generated key with its exact value, plus sentinel values for the
 * three context keys. The property asserts the merged result carries every extracted key losslessly, keeps the exact
 * map size (context keys + extracted keys, collisions notwithstanding), and lets the context win every collision.
 *
 * @since 2026-08
 */
class LargeParamsMergePropertyTest {

    private static final List<String> PARAM_BASES = List.of(
            "gnb.cell",
            "gnb.carrier",
            "amf.slice",
            "amf.paging",
            "smf.pdu_session",
            "smf.charging",
            "upf.tunnel",
            "upf.queuing",
            "ne.power",
            "ne.cooling",
            "transport.bearer",
            "transport.latency");

    @Property(tries = 300, seed = "20260930")
    void largeTelecomParameterSpacesMergeLosslessly(
            @ForAll("languages") String language,
            @ForAll("contexts") NegotiationContext context,
            @ForAll("telecomParams") Map<String, Object> params) {
        Map<String, Object> withCollisions = new LinkedHashMap<>(params);
        withCollisions.put("id", "sentinel-session-id");
        withCollisions.put("round", -999);
        withCollisions.put("maxRounds", -1);
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            properties.put(entry.getKey(), PropertyHarness.typeSchema(entry.getValue()));
        }
        ScriptedNegotiationLlmClient llm =
                PropertyHarness.scripted(PropertyHarness.semanticVerdict("information", withCollisions));
        NegotiationContentService service = PropertyHarness.service(language, llm);
        FilledParamData filled = service.validateProposePromptAndDataFilling(
                "Telecom network element parameter filling request.",
                context,
                PropertyHarness.objectSchema(properties),
                PropertyHarness.templateUri("Negotiation-T/information-negotiation/propose/v1"));
        assertEquals(context.id(), filled.data().get("id"), "the context id must win the collision");
        assertEquals(context.round(), filled.data().get("round"), "the context round must win the collision");
        assertEquals(context.maxRounds(), filled.data().get("maxRounds"), "the context maxRounds must win the collision");
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            assertEquals(
                    entry.getValue(),
                    filled.data().get(entry.getKey()),
                    "extracted parameter '" + entry.getKey() + "' must survive the merge");
        }
        assertEquals(params.size() + 3, filled.data().size(), "merged size = context keys + non-colliding extracted keys");
        assertEquals(1, llm.callCount());
    }

    // ------------------------------------------------------------------ providers

    @Provide
    Arbitrary<String> languages() {
        return PropertyArbitraries.languages();
    }

    @Provide
    Arbitrary<NegotiationContext> contexts() {
        return PropertyArbitraries.contexts();
    }

    @Provide
    Arbitrary<Map<String, Object>> telecomParams() {
        return Arbitraries.maps(telecomParamKeys(), telecomParamValues()).ofMinSize(50).ofMaxSize(80);
    }

    private static Arbitrary<String> telecomParamKeys() {
        return Combinators.combine(Arbitraries.of(PARAM_BASES), Arbitraries.integers().between(0, 99))
                .as((base, index) -> base + "." + index);
    }

    private static Arbitrary<Object> telecomParamValues() {
        return Arbitraries.oneOf(
                Arbitraries.strings()
                        .withChars("abcdefghijklmnopqrstuvwxyz_0123456789")
                        .ofMinLength(1)
                        .ofMaxLength(12),
                Arbitraries.integers().between(-1000, 100000),
                Arbitraries.of(true, false),
                Arbitraries.doubles().between(0.0, 100.0));
    }
}
