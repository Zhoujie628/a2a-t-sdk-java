package net.openan.a2at.sample.negotiation.fromtext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sample.negotiation.shared.NegotiationSampleSupport;
import net.openan.a2at.sample.negotiation.shared.ScenarioData;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;

/**
 * Sample that demonstrates all three fromText negotiation APIs across all three negotiation types.
 *
 * <p>This sample is the verification artifact for the fromText API surface:
 *
 * <ul>
 *   <li>{@link A2ATClient#generateNegotiationProposePromptFromText generateNegotiationProposePromptFromText}
 *   <li>{@link A2ATClient#generateNegotiationAcceptPromptFromText generateNegotiationAcceptPromptFromText}
 *   <li>{@link A2ATClient#generateNegotiationRejectPromptFromText generateNegotiationRejectPromptFromText}
 * </ul>
 *
 * Each API is exercised for all three negotiation types (information, target, feasibility). Unlike the fromData
 * variant, the input is natural-language text: the SDK runs one LLM content-extraction step to parse it into typed
 * content, then renders deterministically. A real LLM API key is required.
 *
 * <p>Run:
 *
 * <pre>{@code java @a2a-t-sample/target/fromtext.javaargs.txt /path/to/.env}</pre>
 *
 * @since 2026-08
 */
public final class FromTextNegotiationSample {

    private FromTextNegotiationSample() {}

    /**
     * Runs all fromText negotiation sample cases and returns one summary per case.
     *
     * @param envPath resolved {@code .env} file path (must contain a real LLM API key)
     * @param logSink log output sink
     * @return summary entries in execution order
     */
    public static List<Map<String, Object>> runAll(Path envPath, Consumer<String> logSink) {
        A2ATClient client = new A2ATClient(envPath);
        List<Map<String, Object>> results = new ArrayList<>();

        results.addAll(informationNegotiation(client, logSink));
        results.addAll(targetNegotiation(client, logSink));
        results.addAll(feasibilityNegotiation(client, logSink));

        return results;
    }

    // =========================================================================================
    // 1. Information negotiation (natural-language text -> LLM extract -> render)
    // =========================================================================================

    private static List<Map<String, Object>> informationNegotiation(A2ATClient client, Consumer<String> logSink) {
        NegotiationSampleSupport.emit(logSink, "\n=== 1. Information negotiation (fromText) ===");
        NegotiationContext ctx = new NegotiationContext(
                NegotiationSampleSupport.SESSION_ID, 1, NegotiationContext.DEFAULT_MAX_ROUNDS, NegotiationPerformative.PROPOSE);
        List<Map<String, Object>> results = new ArrayList<>();

        Map<String, Object> cases =
                ScenarioData.fromTextCases().get("information") instanceof Map<?, ?> map ? asStringMap(map) : Map.of();

        // propose: ask for missing information items
        MetadataContent propose = client.generateNegotiationProposePromptFromText(
                text(cases, "propose"), ctx, NegotiationSampleSupport.INFO_PROPOSE_URI);
        results.add(NegotiationSampleSupport.summary("information", "propose", propose, logSink));

        // accept: deliver the requested information
        MetadataContent accept = client.generateNegotiationAcceptPromptFromText(
                text(cases, "accept"), ctx, NegotiationSampleSupport.INFO_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("information", "accept", accept, logSink));

        // reject: cannot provide the information
        MetadataContent reject = client.generateNegotiationRejectPromptFromText(
                text(cases, "reject"), ctx, NegotiationSampleSupport.INFO_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("information", "reject", reject, logSink));

        return results;
    }

    // =========================================================================================
    // 2. Target negotiation
    // =========================================================================================

    private static List<Map<String, Object>> targetNegotiation(A2ATClient client, Consumer<String> logSink) {
        NegotiationSampleSupport.emit(logSink, "\n=== 2. Target negotiation (fromText) ===");
        NegotiationContext ctx = new NegotiationContext(
                NegotiationSampleSupport.SESSION_ID, 1, NegotiationContext.DEFAULT_MAX_ROUNDS, NegotiationPerformative.PROPOSE);
        List<Map<String, Object>> results = new ArrayList<>();

        Map<String, Object> cases =
                ScenarioData.fromTextCases().get("target") instanceof Map<?, ?> map ? asStringMap(map) : Map.of();

        // propose: state the target intent and request clarification
        MetadataContent propose = client.generateNegotiationProposePromptFromText(
                text(cases, "propose"), ctx, NegotiationSampleSupport.TARGET_PROPOSE_URI);
        results.add(NegotiationSampleSupport.summary("target", "propose", propose, logSink));

        // propose (round 2): the target is clarified and the executor requests confirmation to proceed
        String proposeConfirmText = text(cases, "propose_confirm");
        if (proposeConfirmText != null) {
            NegotiationContext laterRoundCtx = new NegotiationContext(
                    NegotiationSampleSupport.SESSION_ID,
                    2,
                    NegotiationContext.DEFAULT_MAX_ROUNDS,
                    NegotiationPerformative.PROPOSE);
            MetadataContent proposeConfirm = client.generateNegotiationProposePromptFromText(
                    proposeConfirmText, laterRoundCtx, NegotiationSampleSupport.TARGET_PROPOSE_URI);
            results.add(NegotiationSampleSupport.summary("target", "propose_confirm", proposeConfirm, logSink));
        }

        // accept: confirm the negotiated target intent
        MetadataContent accept = client.generateNegotiationAcceptPromptFromText(
                text(cases, "accept"), ctx, NegotiationSampleSupport.TARGET_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("target", "accept", accept, logSink));

        // reject: cannot agree on the target
        MetadataContent reject = client.generateNegotiationRejectPromptFromText(
                text(cases, "reject"), ctx, NegotiationSampleSupport.TARGET_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("target", "reject", reject, logSink));

        return results;
    }

    // =========================================================================================
    // 3. Feasibility negotiation
    // =========================================================================================

    private static List<Map<String, Object>> feasibilityNegotiation(A2ATClient client, Consumer<String> logSink) {
        NegotiationSampleSupport.emit(logSink, "\n=== 3. Feasibility negotiation (fromText) ===");
        NegotiationContext ctx = new NegotiationContext(
                NegotiationSampleSupport.SESSION_ID, 1, NegotiationContext.DEFAULT_MAX_ROUNDS, NegotiationPerformative.PROPOSE);
        List<Map<String, Object>> results = new ArrayList<>();

        Map<String, Object> cases =
                ScenarioData.fromTextCases().get("feasibility") instanceof Map<?, ?> map ? asStringMap(map) : Map.of();

        // propose: request a feasibility evaluation
        MetadataContent propose = client.generateNegotiationProposePromptFromText(
                text(cases, "propose"), ctx, NegotiationSampleSupport.FEASIBILITY_PROPOSE_URI);
        results.add(NegotiationSampleSupport.summary("feasibility", "propose", propose, logSink));

        // propose (round 2): the assessment is complete and the executor requests confirmation to proceed
        String proposeConfirmText = text(cases, "propose_confirm");
        if (proposeConfirmText != null) {
            MetadataContent proposeConfirm = client.generateNegotiationProposePromptFromText(
                    proposeConfirmText, ctx, NegotiationSampleSupport.FEASIBILITY_PROPOSE_URI);
            results.add(NegotiationSampleSupport.summary("feasibility", "propose_confirm", proposeConfirm, logSink));
        }

        // accept: feasibility confirmed
        MetadataContent accept = client.generateNegotiationAcceptPromptFromText(
                text(cases, "accept"), ctx, NegotiationSampleSupport.FEASIBILITY_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("feasibility", "accept", accept, logSink));

        // reject: not feasible
        MetadataContent reject = client.generateNegotiationRejectPromptFromText(
                text(cases, "reject"), ctx, NegotiationSampleSupport.FEASIBILITY_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("feasibility", "reject", reject, logSink));

        return results;
    }

    private static String text(Map<String, Object> cases, String key) {
        Object value = ScenarioData.resolveValue(String.valueOf(cases.get(key)));
        return value == null || "null".equals(value) ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
    /**
     * Entry point.
     *
     * @param args first argument is the {@code .env} file path (must contain a real LLM API key)
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java FromTextNegotiationSample <path-to-.env>");
            System.err.println("A real LLM API key is required: fromText runs an LLM extraction step for every case.");
            System.exit(1);
        }
        Path envPath = Path.of(args[0]);
        List<Map<String, Object>> results = runAll(envPath, System.out::println);
        System.out.println("\n=== Summary (" + results.size() + " cases) ===");
        for (Map<String, Object> r : results) {
            System.out.println(r);
        }
    }
}
