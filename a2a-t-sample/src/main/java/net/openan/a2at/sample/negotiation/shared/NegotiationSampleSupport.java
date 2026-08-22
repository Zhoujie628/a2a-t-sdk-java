package net.openan.a2at.sample.negotiation.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;

/**
 * Shared helpers for the fromData and fromText negotiation samples.
 *
 * <p>Both samples produce the same {@link MetadataContent} output structure; they differ only in how they construct the
 * input (typed records vs natural-language text). This class centralises the session id, the template-URI constants,
 * the logging helper and the summary builder so neither sample duplicates them.
 *
 * @since 2026-08
 */
public final class NegotiationSampleSupport {

    /** Fixed negotiation session id shared by every sample case (matches the golden fixtures). */
    public static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    /** Information negotiation template URIs. */
    public static final TemplateUri INFO_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    public static final TemplateUri INFO_ACCEPT_REJECT_URI = StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT;

    /** Target negotiation template URIs. */
    public static final TemplateUri TARGET_PROPOSE_URI = StandardTemplates.TARGET_NEGOTIATION_PROPOSE;

    public static final TemplateUri TARGET_ACCEPT_REJECT_URI = StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT;

    /** Feasibility negotiation template URIs. */
    public static final TemplateUri FEASIBILITY_PROPOSE_URI = StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE;

    public static final TemplateUri FEASIBILITY_ACCEPT_REJECT_URI =
            StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT;

    private NegotiationSampleSupport() {}

    /**
     * Builds a one-line summary entry for one sample case.
     *
     * @param type negotiation type label (information / target / feasibility)
     * @param phase phase label (propose / accept / reject)
     * @param mc generated metadata content
     * @param logSink log output sink
     * @return summary map
     */
    public static Map<String, Object> summary(String type, String phase, MetadataContent mc, Consumer<String> logSink) {
        emit(logSink, "  [" + type + "/" + phase + "] templateUri=" + mc.templateUri());
        String preview = mc.promptText().length() > 120 ? mc.promptText().substring(0, 120) + "..." : mc.promptText();
        emit(logSink, "    promptText: " + preview);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", type);
        entry.put("phase", phase);
        entry.put("templateUri", mc.templateUri());
        entry.put("extensionUri", mc.extensionUri());
        entry.put("promptLength", mc.promptText().length());
        return entry;
    }

    /** Emits one log line if the sink is non-null. */
    public static void emit(Consumer<String> logSink, String message) {
        if (logSink != null) {
            logSink.accept(message);
        }
    }
}
