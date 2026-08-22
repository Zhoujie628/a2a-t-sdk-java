package net.openan.a2at.sample.negotiation.shared;

import java.util.List;
import java.util.Map;

/**
 * Fixed scenario data for the SPN private-line-complaint diagnosis: one map with missing required params (triggering
 * negotiation) and one with the params filled in (completing the negotiation).
 *
 * <p>The slot keys ({@code 任务对象} / {@code 任务上下文}) match the bundled {@code private-line-complaint} slot schema. An
 * empty {@code 任务对象} triggers the server-side information negotiation (message 2); filling it in completes the
 * negotiation (message 3-4).
 *
 * @since 2026-08
 */
public final class ScenarioData {

    private ScenarioData() {}

    /** Slot schema describing the Task-T parameters (passed to validateAndFillingTaskData). */
    public static final Map<String, Object> TASK_SCHEMA = Map.of(
            "type", "object",
            "properties",
                    Map.of(
                            "任务对象", Map.of("type", "string"),
                            "任务上下文", Map.of("type", "string")),
            "required", List.of("任务上下文"));

    /**
     * Scenario data with missing required params: the access port name is empty and the complaint category is blank,
     * which triggers the server-side information negotiation.
     */
    public static Map<String, Object> missingParams() {
        return Map.of(
                "任务对象",
                "",
                "任务上下文",
                "2. 问题发生时间：2026-05-11T08:21:46Z\n"
                        + "3. OSS侧事件流水号：event-id-20260511-09013\n"
                        + "4. 投诉详情：深圳访问广州响应延迟骤升，交易接口频繁报连接超时");
    }

    /**
     * Scenario data with all params filled in: the access port name and complaint category are provided, completing the
     * negotiation so the server can run the diagnosis.
     */
    public static Map<String, Object> filledParams() {
        return Map.of(
                "任务对象",
                "P781-珠江新城-PTN7900-23-TPA1EG24-17",
                "任务上下文",
                "1. 投诉分类：专线质差\n"
                        + "2. 问题发生时间：2026-05-11T08:21:46Z\n"
                        + "3. OSS侧事件流水号：event-id-20260511-09013\n"
                        + "4. 投诉详情：深圳访问广州响应延迟骤升，交易接口频繁报连接超时");
    }
}
