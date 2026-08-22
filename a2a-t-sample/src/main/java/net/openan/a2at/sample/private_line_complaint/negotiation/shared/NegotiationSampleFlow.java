package net.openan.a2at.sample.private_line_complaint.negotiation.shared;

import java.util.Map;
import java.util.UUID;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.server.A2ATServer;

/** Runs the six Negotiation-T APIs used by the private-line complaint sample. */
public final class NegotiationSampleFlow {

    public static final TemplateUri PROPOSE_TEMPLATE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    public static final TemplateUri ENDING_TEMPLATE_URI = StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT;

    private NegotiationSampleFlow() {
    }

    public static NegotiationFlowResult run(
            A2ATClient client,
            A2ATServer server,
            NegotiationScenario scenario,
            NegotiationDecision decision) {
        NegotiationContext requestContext = new NegotiationContext(UUID.randomUUID().toString(), 1, 3);
        MetadataContent propose =
                client.generateNegotiationProposePromptFromText(scenario.proposeText(), requestContext, PROPOSE_TEMPLATE_URI);
        String proposePrompt = NegotiationMetadataReader.readPrompt(propose.buildMetadataContent(), PROPOSE_TEMPLATE_URI);
        FilledParamData proposeData =
                server.validateProposePromptAndDataFilling(proposePrompt, InformationNegotiationSchemas.propose(), PROPOSE_TEMPLATE_URI);

        NegotiationContext responseContext = contextFrom(proposeData.data());
        MetadataContent ending = decision == NegotiationDecision.ACCEPT
                ? server.generateNegotiationAcceptPromptFromText(scenario.acceptText(), responseContext, ENDING_TEMPLATE_URI)
                : server.generateNegotiationRejectPromptFromText(scenario.rejectText(), responseContext, ENDING_TEMPLATE_URI);
        String endingPrompt = NegotiationMetadataReader.readPrompt(ending.buildMetadataContent(), ENDING_TEMPLATE_URI);
        FilledParamData endingData = decision == NegotiationDecision.ACCEPT
                ? client.validateAcceptPromptAndDataFilling(
                        endingPrompt, InformationNegotiationSchemas.accept(), ENDING_TEMPLATE_URI)
                : client.validateRejectPromptAndDataFilling(
                        endingPrompt, InformationNegotiationSchemas.reject(), ENDING_TEMPLATE_URI);
        return new NegotiationFlowResult(requestContext, propose, proposeData, ending, endingData, decision);
    }

    public static NegotiationContext contextFrom(Map<String, Object> data) {
        Object id = data.get("id");
        Object round = data.get("round");
        Object maxRounds = data.get("maxRounds");
        if (!(id instanceof String text) || !(round instanceof Number roundNumber) || !(maxRounds instanceof Number maxRoundsNumber)) {
            throw new IllegalArgumentException("Filled negotiation data does not contain a valid context");
        }
        return new NegotiationContext(text, roundNumber.intValue(), maxRoundsNumber.intValue());
    }

    public record NegotiationFlowResult(
            NegotiationContext requestContext,
            MetadataContent propose,
            FilledParamData proposeData,
            MetadataContent ending,
            FilledParamData endingData,
            NegotiationDecision decision) {
    }
}
