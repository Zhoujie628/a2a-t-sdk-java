package net.openan.a2at.sample.negotiation.fromtext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sample.negotiation.shared.NegotiationSampleSupport;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;

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
        NegotiationContext ctx =
                new NegotiationContext(NegotiationSampleSupport.SESSION_ID, 1, NegotiationContext.DEFAULT_MAX_ROUNDS);
        List<Map<String, Object>> results = new ArrayList<>();

        // propose: ask for missing information items
        String proposeText =
                "请提供以下缺失信息：" + "1. 接入端口名称：请提供业务接入端口名称；" + "2. 投诉分类：专线中断或专线质差。" + "两个参数均为必选，缺少无法启动诊断。" + "两者共同定位专线故障原因。";
        MetadataContent propose = client.generateNegotiationProposePromptFromText(
                proposeText, ctx, NegotiationSampleSupport.INFO_PROPOSE_URI);
        results.add(NegotiationSampleSupport.summary("information", "propose", propose, logSink));

        // accept: deliver the requested information
        String acceptText =
                "同意补充以下信息：" + "1. 接入端口名称：P781-珠江新城-PTN7900-23-TPA1EG24-17；" + "2. 投诉分类：专线质差。" + "信息已完整，可以启动诊断。";
        MetadataContent accept = client.generateNegotiationAcceptPromptFromText(
                acceptText, ctx, NegotiationSampleSupport.INFO_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("information", "accept", accept, logSink));

        // reject: cannot provide the information
        String rejectText = "拒绝补充信息：" + "接入端口名称因站点清单不可用而无法提供，" + "本次协商结束。";
        MetadataContent reject = client.generateNegotiationRejectPromptFromText(
                rejectText, ctx, NegotiationSampleSupport.INFO_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("information", "reject", reject, logSink));

        return results;
    }

    // =========================================================================================
    // 2. Target negotiation
    // =========================================================================================

    private static List<Map<String, Object>> targetNegotiation(A2ATClient client, Consumer<String> logSink) {
        NegotiationSampleSupport.emit(logSink, "\n=== 2. Target negotiation (fromText) ===");
        NegotiationContext ctx =
                new NegotiationContext(NegotiationSampleSupport.SESSION_ID, 1, NegotiationContext.DEFAULT_MAX_ROUNDS);
        List<Map<String, Object>> results = new ArrayList<>();

        // propose: state the target intent and request clarification
        String proposeText = "对无线节点节能优化任务的意图理解如下：" + "任务意图：08:00-18:00对目标站点启用无线节点节能优化。" + "待澄清内容：节能区域松山湖还是其他站点？";
        MetadataContent propose = client.generateNegotiationProposePromptFromText(
                proposeText, ctx, NegotiationSampleSupport.TARGET_PROPOSE_URI);
        results.add(NegotiationSampleSupport.summary("target", "propose", propose, logSink));

        // accept: confirm the negotiated target intent
        String acceptText = "确认意图：08:00-18:00对松山湖站点启用无线节点节能优化，" + "速率保障不低于10Mbps。" + "最终意图已明确，可以执行。";
        MetadataContent accept = client.generateNegotiationAcceptPromptFromText(
                acceptText, ctx, NegotiationSampleSupport.TARGET_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("target", "accept", accept, logSink));

        // reject: cannot agree on the target
        String rejectText = "拒绝：节能区域信息因站点清单不可用而无法完整澄清，" + "本次协商结束。";
        MetadataContent reject = client.generateNegotiationRejectPromptFromText(
                rejectText, ctx, NegotiationSampleSupport.TARGET_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("target", "reject", reject, logSink));

        return results;
    }

    // =========================================================================================
    // 3. Feasibility negotiation
    // =========================================================================================

    private static List<Map<String, Object>> feasibilityNegotiation(A2ATClient client, Consumer<String> logSink) {
        NegotiationSampleSupport.emit(logSink, "\n=== 3. Feasibility negotiation (fromText) ===");
        NegotiationContext ctx =
                new NegotiationContext(NegotiationSampleSupport.SESSION_ID, 1, NegotiationContext.DEFAULT_MAX_ROUNDS);
        List<Map<String, Object>> results = new ArrayList<>();

        // propose: request a feasibility evaluation
        String proposeText = "请评估无线节点节能优化在高峰时段是否可行。" + "评估对象：松山湖站点08:00-18:00节能窗口。" + "评估约束：速率保障不低于10Mbps。";
        MetadataContent propose = client.generateNegotiationProposePromptFromText(
                proposeText, ctx, NegotiationSampleSupport.FEASIBILITY_PROPOSE_URI);
        results.add(NegotiationSampleSupport.summary("feasibility", "propose", propose, logSink));

        // accept: feasibility confirmed
        String acceptText = "同意：高峰时段节能优化可行，" + "速率保障满足10Mbps下限，可启用。" + "评估结果：可行。";
        MetadataContent accept = client.generateNegotiationAcceptPromptFromText(
                acceptText, ctx, NegotiationSampleSupport.FEASIBILITY_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("feasibility", "accept", accept, logSink));

        // reject: not feasible
        String rejectText = "拒绝：节能目标在现有供电约束下无法实现，" + "需调整方案。评估结果：不可行。";
        MetadataContent reject = client.generateNegotiationRejectPromptFromText(
                rejectText, ctx, NegotiationSampleSupport.FEASIBILITY_ACCEPT_REJECT_URI);
        results.add(NegotiationSampleSupport.summary("feasibility", "reject", reject, logSink));

        return results;
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
