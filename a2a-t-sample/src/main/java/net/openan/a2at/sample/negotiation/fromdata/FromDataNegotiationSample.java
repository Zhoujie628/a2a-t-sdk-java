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
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
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

        // propose: request missing information items
        MetadataContent propose = client.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        ctx,
                        new InformationProposeContent(
                                List.of(
                                        new NegotiationItem("接入端口名称", "请提供业务接入端口名称"),
                                        new NegotiationItem("投诉分类", "专线中断或专线质差")),
                                "两个参数均为必选，缺少无法启动诊断")),
                NegotiationSampleSupport.INFO_PROPOSE_URI);
        results.add(NegotiationSampleSupport.summary("information", "propose", propose, logSink));

        // accept: deliver the missing information
        MetadataContent accept = client.generateNegotiationAcceptPromptFromData(
                new NegotiationEndingData(
                        ctx,
                        new InformationEndingContent(
                                NegotiationConclusion.ACCEPT,
                                List.of(
                                        new NegotiationItem(
                                                "接入端口名称",
                                                String.valueOf(ScenarioData.filledParams()
                                                        .get("任务对象"))),
                                        new NegotiationItem("投诉分类", "专线质差")))),
                NegotiationSampleSupport.INFO_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("information", "accept", accept, logSink));

        // reject: cannot provide the information
        MetadataContent reject = client.generateNegotiationRejectPromptFromData(
                new NegotiationEndingData(
                        ctx,
                        new InformationEndingContent(
                                NegotiationConclusion.REJECT, List.of(new NegotiationItem("接入端口名称", "站点清单不可用，无法提供")))),
                NegotiationSampleSupport.INFO_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("information", "reject", reject, logSink));

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

        // propose: state the target intent and request clarification
        MetadataContent propose = client.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        ctx,
                        new TargetProposeContent(
                                "对无线节点节能优化任务的意图理解待澄清",
                                List.of(new NegotiationItem("任务意图", "08:00-18:00对目标站点启用无线节点节能优化")),
                                null,
                                List.of(new NegotiationItem("节能区域", "松山湖还是其他站点？")))),
                NegotiationSampleSupport.TARGET_PROPOSE_URI);
        results.add(NegotiationSampleSupport.summary("target", "propose", propose, logSink));

        // accept: confirm the negotiated target intent
        MetadataContent accept = client.generateNegotiationAcceptPromptFromData(
                new NegotiationEndingData(
                        ctx,
                        new TargetEndingContent(
                                NegotiationConclusion.ACCEPT,
                                "最终确认意图：08:00-18:00对松山湖站点启用无线节点节能优化，速率保障不低于10Mbps",
                                null)),
                NegotiationSampleSupport.TARGET_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("target", "accept", accept, logSink));

        // reject: cannot agree on the target
        MetadataContent reject = client.generateNegotiationRejectPromptFromData(
                new NegotiationEndingData(
                        ctx, new TargetEndingContent(NegotiationConclusion.REJECT, null, "节能区域信息因站点清单不可用而无法完整澄清")),
                NegotiationSampleSupport.TARGET_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("target", "reject", reject, logSink));

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

        // propose: request a feasibility evaluation
        MetadataContent propose = client.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        ctx,
                        new FeasibilityProposeContent(
                                "请评估无线节点节能优化在高峰时段是否可行",
                                NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                                List.of(
                                        new NegotiationItem("评估对象", "松山湖站点08:00-18:00节能窗口"),
                                        new NegotiationItem("评估约束", "速率保障不低于10Mbps")),
                                null)),
                NegotiationSampleSupport.FEASIBILITY_PROPOSE_URI);
        results.add(NegotiationSampleSupport.summary("feasibility", "propose", propose, logSink));

        // accept: feasibility confirmed
        MetadataContent accept = client.generateNegotiationAcceptPromptFromData(
                new NegotiationEndingData(
                        ctx,
                        new FeasibilityEndingContent(NegotiationConclusion.ACCEPT, "高峰时段节能优化可行，速率保障满足10Mbps下限，可启用")),
                NegotiationSampleSupport.FEASIBILITY_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("feasibility", "accept", accept, logSink));

        // reject: not feasible
        MetadataContent reject = client.generateNegotiationRejectPromptFromData(
                new NegotiationEndingData(
                        ctx, new FeasibilityEndingContent(NegotiationConclusion.REJECT, "节能目标在现有供电约束下无法实现，需调整方案")),
                NegotiationSampleSupport.FEASIBILITY_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("feasibility", "reject", reject, logSink));

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
