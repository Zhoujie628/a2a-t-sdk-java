package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import java.util.Map;

/** One end-to-end negotiation evaluation flow assembled from the labelled generation corpus. */
public record NegotiationEvaluationFlowCase(
        String id,
        String category,
        String decision,
        NegotiationEvaluationCase proposeCase,
        NegotiationEvaluationCase endingCase) {

    public Map<String, Object> expectedPropose() {
        return proposeCase.expected();
    }

    public Map<String, Object> expectedEnding() {
        return endingCase.expected();
    }

    public String clientSupplement() {
        String header = "## 客户端补充信息\n";
        if ("accept".equals(decision)) {
            return header
                    + "1. 接入端口名称：" + expectedEnding().get("access_port_name")
                    + "\n2. 投诉分类：" + expectedEnding().get("complaint_category");
        }
        return header + "无法补充所需信息，原因：" + expectedEnding().get("rejection_reason");
    }

    public String endingGenerationText() {
        return clientSupplement()
                + "\n\n## 服务端协商结论生成要求\n"
                + endingCase.text();
    }
}
