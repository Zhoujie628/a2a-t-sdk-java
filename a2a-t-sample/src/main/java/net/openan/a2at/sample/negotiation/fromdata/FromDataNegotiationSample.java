package net.openan.a2at.sample.negotiation.fromdata;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sample.negotiation.shared.NegotiationSampleSupport;
import net.openan.a2at.sample.negotiation.shared.ScenarioData;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;

/**
 * Sample that demonstrates all three fromData negotiation APIs across all three negotiation types.
 *
 * <p>This sample is the verification artifact for the fromData API surface:
 *
 * <ul>
 *   <li>{@link A2ATClient#generateNegotiationProposePromptFromData generateNegotiationProposePromptFromData}
 *   <li>{@link A2ATClient#generateNegotiationAcceptPromptFromData generateNegotiationAcceptPromptFromData}
 *   <li>{@link A2ATClient#generateNegotiationRejectPromptFromData generateNegotiationRejectPromptFromData}
 * </ul>
 *
 * Each API is exercised for all three negotiation types (information, target, feasibility) so the 3x3 matrix (type x
 * phase) is fully covered. Generation is deterministic (no LLM call for rendering), but the SDK still requires a
 * configured LLM API key in the {@code .env} file.
 *
 * <p>Run:
 *
 * <pre>{@code java @a2a-t-sample/target/fromdata.javaargs.txt /path/to/.env}</pre>
 *
 * @since 2026-08
 */
public final class FromDataNegotiationSample {

    private FromDataNegotiationSample() {}

    /**
     * Runs all fromData negotiation sample cases and returns one summary per case.
     *
     * @param envPath resolved {@code .env} file path
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
    // 1. Information negotiation (missing information request / accept / reject)
    // =========================================================================================

    private static List<Map<String, Object>> informationNegotiation(A2ATClient client, Consumer<String> logSink) {
        NegotiationSampleSupport.emit(logSink, "\n=== 1. Information negotiation ===");
        NegotiationContext ctx =
                new NegotiationContext(NegotiationSampleSupport.SESSION_ID, 1, NegotiationContext.DEFAULT_MAX_ROUNDS);
        List<Map<String, Object>> results = new ArrayList<>();

        Map<String, Object> section = ScenarioData.caseSection("information");

        // propose: request missing information items
        Map<String, Object> propose = ScenarioData.map(section, "propose");
        MetadataContent proposeResult = client.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        ctx,
                        new InformationProposeContent(
                                ScenarioData.items(propose, "items"), ScenarioData.text(propose, "relationship"))),
                NegotiationSampleSupport.INFO_PROPOSE_URI);
        results.add(NegotiationSampleSupport.summary("information", "propose", proposeResult, logSink));

        // accept: deliver the missing information
        Map<String, Object> accept = ScenarioData.map(section, "accept");
        MetadataContent acceptResult = client.generateNegotiationAcceptPromptFromData(
                new NegotiationEndingData(
                        ctx,
                        new InformationEndingContent(
                                NegotiationConclusion.ACCEPT, ScenarioData.items(accept, "items"))),
                NegotiationSampleSupport.INFO_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("information", "accept", acceptResult, logSink));

        // reject: cannot provide the information
        Map<String, Object> reject = ScenarioData.map(section, "reject");
        MetadataContent rejectResult = client.generateNegotiationRejectPromptFromData(
                new NegotiationEndingData(
                        ctx,
                        new InformationEndingContent(
                                NegotiationConclusion.REJECT, ScenarioData.items(reject, "items"))),
                NegotiationSampleSupport.INFO_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("information", "reject", rejectResult, logSink));

        return results;
    }

    // =========================================================================================
    // 2. Target negotiation (intent alignment / accept / reject)
    // =========================================================================================

    private static List<Map<String, Object>> targetNegotiation(A2ATClient client, Consumer<String> logSink) {
        NegotiationSampleSupport.emit(logSink, "\n=== 2. Target negotiation ===");
        NegotiationContext ctx =
                new NegotiationContext(NegotiationSampleSupport.SESSION_ID, 1, NegotiationContext.DEFAULT_MAX_ROUNDS);
        List<Map<String, Object>> results = new ArrayList<>();

        Map<String, Object> section = ScenarioData.caseSection("target");

        // propose: state the target intent and request clarification
        Map<String, Object> propose = ScenarioData.map(section, "propose");
        MetadataContent proposeResult = client.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        ctx,
                        new TargetProposeContent(
                                ScenarioData.text(propose, "description"),
                                ScenarioData.items(propose, "intent_understanding"),
                                ScenarioData.items(propose, "alignment_and_clarification"),
                                ScenarioData.items(propose, "request_for_clarification"))),
                NegotiationSampleSupport.TARGET_PROPOSE_URI);
        results.add(NegotiationSampleSupport.summary("target", "propose", proposeResult, logSink));

        // accept: confirm the negotiated target intent
        Map<String, Object> accept = ScenarioData.map(section, "accept");
        MetadataContent acceptResult = client.generateNegotiationAcceptPromptFromData(
                new NegotiationEndingData(
                        ctx,
                        new TargetEndingContent(
                                NegotiationConclusion.ACCEPT, ScenarioData.text(accept, "confirmed_intent"), null)),
                NegotiationSampleSupport.TARGET_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("target", "accept", acceptResult, logSink));

        // reject: cannot agree on the target
        Map<String, Object> reject = ScenarioData.map(section, "reject");
        MetadataContent rejectResult = client.generateNegotiationRejectPromptFromData(
                new NegotiationEndingData(
                        ctx,
                        new TargetEndingContent(
                                NegotiationConclusion.REJECT, null, ScenarioData.text(reject, "rejection_reason"))),
                NegotiationSampleSupport.TARGET_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("target", "reject", rejectResult, logSink));

        return results;
    }

    // =========================================================================================
    // 3. Feasibility negotiation (evaluation request / accept / reject)
    // =========================================================================================

    private static List<Map<String, Object>> feasibilityNegotiation(A2ATClient client, Consumer<String> logSink) {
        NegotiationSampleSupport.emit(logSink, "\n=== 3. Feasibility negotiation ===");
        NegotiationContext ctx =
                new NegotiationContext(NegotiationSampleSupport.SESSION_ID, 1, NegotiationContext.DEFAULT_MAX_ROUNDS);
        List<Map<String, Object>> results = new ArrayList<>();

        Map<String, Object> section = ScenarioData.caseSection("feasibility");

        // propose: request a feasibility evaluation
        Map<String, Object> propose = ScenarioData.map(section, "propose");
        MetadataContent proposeResult = client.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        ctx,
                        new FeasibilityProposeContent(
                                ScenarioData.text(propose, "description"),
                                NegotiationAction.valueOf(ScenarioData.text(propose, "action")),
                                ScenarioData.items(propose, "contents_to_evaluate"),
                                ScenarioData.items(propose, "infeasibility_details_and_proposal"))),
                NegotiationSampleSupport.FEASIBILITY_PROPOSE_URI);
        results.add(NegotiationSampleSupport.summary("feasibility", "propose", proposeResult, logSink));

        // accept: feasibility confirmed
        Map<String, Object> accept = ScenarioData.map(section, "accept");
        MetadataContent acceptResult = client.generateNegotiationAcceptPromptFromData(
                new NegotiationEndingData(
                        ctx,
                        new FeasibilityEndingContent(
                                NegotiationConclusion.ACCEPT, ScenarioData.text(accept, "summary"))),
                NegotiationSampleSupport.FEASIBILITY_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("feasibility", "accept", acceptResult, logSink));

        // reject: not feasible
        Map<String, Object> reject = ScenarioData.map(section, "reject");
        MetadataContent rejectResult = client.generateNegotiationRejectPromptFromData(
                new NegotiationEndingData(
                        ctx,
                        new FeasibilityEndingContent(
                                NegotiationConclusion.REJECT, ScenarioData.text(reject, "summary"))),
                NegotiationSampleSupport.FEASIBILITY_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("feasibility", "reject", rejectResult, logSink));

        return results;
    }

    // =========================================================================================

    /**
     * Entry point.
     *
     * @param args first argument is the {@code .env} file path (must contain a real LLM API key)
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java FromDataNegotiationSample <path-to-.env>");
            System.err.println("A configured LLM API key is required (fromData skips the LLM only for "
                    + "negotiation-message generation).");
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
