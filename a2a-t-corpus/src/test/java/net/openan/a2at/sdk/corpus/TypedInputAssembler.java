package net.openan.a2at.sdk.corpus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.databind.JsonNode;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import org.jspecify.annotations.Nullable;

/**
 * Assembles the typed input data of the from-data family and the differential runs from the corpus {@code input.data}
 * JSON node.
 *
 * <p>The corpus carries the typed content in the same snake_case shape the LLM extraction produces
 * (design document §8.2: the case author writes the typed data explicitly), so this assembler only converts that JSON
 * onto the typed content records — it never invents or reconstructs content. Deliberate performative-content
 * contradictions (an {@code Accept} conclusion fed to the reject API) pass through unvalidated so the production
 * from-data validation surfaces them.
 *
 * <p>A malformed {@code input.data} node is a corpus authoring defect, not a production behavior: the assembler fails
 * with an {@link IllegalStateException} naming the offending field, which no corpus expectation can match by accident.
 *
 * @since 2026-08
 */
final class TypedInputAssembler {

    private TypedInputAssembler() {}

    /**
     * Assembles the typed API input of one from-data call.
     *
     * @param data typed content in the snake_case corpus shape
     * @param context negotiation context of the case, or null for the null-context probes
     * @param templateUri template URI addressed by the case, or null for the null-URI probes
     * @param performative performative of the addressed generation method
     * @param language language of the case
     * @return a {@code NegotiationProposeData}, {@code NegotiationEndingData} or {@code NegotiationAbortData}
     */
    static Object assemble(
            JsonNode data,
            @Nullable NegotiationContext context,
            @Nullable TemplateUri templateUri,
            NegotiationPerformative performative,
            String language) {
        Objects.requireNonNull(data, "data");
        NegotiationType type = resolveType(templateUri, performative, language);
        return switch (performative) {
            case PROPOSE -> new NegotiationProposeData(context, proposeContent(data, type));
            case ACCEPT, REJECT -> new NegotiationEndingData(context, endingContent(data, type));
            case ABORT -> new NegotiationAbortData(
                    context, new NegotiationAbortContent(requiredText(data, "termination_reason")));
        };
    }

    // ------------------------------------------------------------------ content mapping

    private static NegotiationProposeContent proposeContent(JsonNode data, @Nullable NegotiationType type) {
        if (type == null) {
            throw authoring("the abort performative carries no typed propose content");
        }
        return switch (type) {
            case INFORMATION -> new InformationProposeContent(
                    requiredItems(data, "items"), optionalText(data, "relationship"));
            case TARGET -> new TargetProposeContent(
                    requiredText(data, "target_negotiation_description"),
                    optionalItems(data, "intent_understanding"),
                    optionalItems(data, "alignment_and_clarification"),
                    optionalItems(data, "request_for_clarification"));
            case FEASIBILITY -> new FeasibilityProposeContent(
                    requiredText(data, "feasibility_negotiation_description"),
                    action(data),
                    optionalItems(data, "contents_to_evaluate"),
                    optionalItems(data, "infeasibility_details_and_proposal"));
        };
    }

    private static NegotiationEndingContent endingContent(JsonNode data, @Nullable NegotiationType type) {
        if (type == null) {
            throw authoring("the abort performative carries no typed ending content");
        }
        NegotiationConclusion conclusion = conclusion(data);
        return switch (type) {
            case INFORMATION -> new InformationEndingContent(conclusion, requiredItems(data, "items"));
            case TARGET -> new TargetEndingContent(
                    conclusion, optionalText(data, "confirmed_intent"), optionalText(data, "failure_reason"));
            case FEASIBILITY -> new FeasibilityEndingContent(
                    conclusion, requiredText(data, "feasibility_summary"));
        };
    }

    private static NegotiationType resolveType(
            @Nullable TemplateUri templateUri, NegotiationPerformative performative, String language) {
        if (templateUri == null) {
            throw authoring("the template URI is missing but the typed input data needs the negotiation type");
        }
        return NegotiationReference.fromTemplateUri(templateUri, performative, language)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Template URI does not address a negotiation template of the expected performative "
                                + performative + " (" + NegotiationReference.uriSegmentOf(performative) + "): "
                                + templateUri.uri() + "."))
                .type();
    }

    // ------------------------------------------------------------------ field readers

    private static NegotiationConclusion conclusion(JsonNode data) {
        JsonNode node = data.path("conclusion");
        if (!node.isTextual()) {
            throw authoring("conclusion must be the Accept or Reject literal");
        }
        for (NegotiationConclusion candidate : NegotiationConclusion.values()) {
            if (candidate.literal().equals(node.asText())) {
                return candidate;
            }
        }
        throw authoring("conclusion must be the Accept or Reject literal but was '" + node.asText() + "'");
    }

    private static NegotiationAction action(JsonNode data) {
        JsonNode node = data.path("action");
        if (!node.isTextual()) {
            throw authoring("action must be one of the two feasibility action names");
        }
        return NegotiationAction.valueOf(node.asText());
    }

    private static String requiredText(JsonNode data, String field) {
        JsonNode node = data.path(field);
        if (!node.isTextual() || node.asText().isBlank()) {
            throw authoring(field + " must be a non-blank string");
        }
        return node.asText();
    }

    private static @Nullable String optionalText(JsonNode data, String field) {
        JsonNode node = data.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw authoring(field + " must be a string or null");
        }
        return node.asText();
    }

    private static List<NegotiationItem> requiredItems(JsonNode data, String field) {
        JsonNode node = data.path(field);
        if (node.isMissingNode() || node.isNull()) {
            throw authoring(field + " must be an array of items");
        }
        return items(node, field);
    }

    private static @Nullable List<NegotiationItem> optionalItems(JsonNode data, String field) {
        JsonNode node = data.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return items(node, field);
    }

    private static List<NegotiationItem> items(JsonNode node, String field) {
        if (!node.isArray()) {
            throw authoring(field + " must be an array of items");
        }
        List<NegotiationItem> items = new ArrayList<>();
        for (JsonNode entry : node) {
            JsonNode name = entry.path("name");
            if (!name.isTextual() || name.asText().isBlank()) {
                throw authoring(field + " contained an item without a name");
            }
            JsonNode value = entry.path("value");
            if (!value.isMissingNode() && !value.isNull() && !value.isTextual()) {
                throw authoring(field + " contained an item whose value is not a string or null");
            }
            items.add(new NegotiationItem(name.asText(), value.isTextual() ? value.asText() : null));
        }
        return List.copyOf(items);
    }

    private static IllegalStateException authoring(String message) {
        return new IllegalStateException("input.data: " + message);
    }
}
