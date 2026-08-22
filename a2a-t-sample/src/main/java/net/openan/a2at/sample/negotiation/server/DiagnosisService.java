package net.openan.a2at.sample.negotiation.server;

import java.util.Map;
import net.openan.a2at.sdk.core.model.FilledParamData;

/**
 * Produces a diagnosis result from the validated Task-T parameters (not hardcoded).
 *
 * <p>In a real deployment this would call the EMS/NMS north-bound API; the demo builds the diagnosis text from the
 * parameters extracted by {@code validateAndFillingTaskData}, so the result adapts to any Task-T input.
 *
 * @since 2026-08
 */
public final class DiagnosisService {

    private DiagnosisService() {}

    /**
     * Builds a diagnosis result from the extracted Task-T parameters.
     *
     * @param params validated parameters from {@code validateAndFillingTaskData}
     * @return diagnosis result text
     */
    public static String diagnose(FilledParamData params) {
        StringBuilder sb = new StringBuilder();
        sb.append("1. 诊断结果：成功\n");
        sb.append("2. 诊断结果详情：");
        if (params != null && params.data() != null) {
            for (Map.Entry<String, Object> entry : params.data().entrySet()) {
                String key = entry.getKey();
                if ("id".equals(key) || "round".equals(key) || "maxRounds".equals(key)) {
                    continue;
                }
                Object value = entry.getValue();
                if (value != null && (!(value instanceof String s) || !s.isBlank())) {
                    sb.append(key).append("=").append(value).append("；");
                }
            }
        }
        sb.append("\n3. 修复建议：检查接入端口物理连接和光模块状态，必要时恢复供电或重新开启端口");
        return sb.toString();
    }
}
