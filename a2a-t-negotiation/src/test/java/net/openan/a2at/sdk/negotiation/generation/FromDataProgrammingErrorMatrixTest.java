package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InfoEndingContent;
import net.openan.a2at.sdk.negotiation.content.InfoProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import org.junit.jupiter.api.Test;

/**
 * Verifies the programming-error matrix of the from-data generation as one table-driven suite.
 *
 * <p>Every row of the input-validation matrix of the from-data variants — method-data mismatch, template URIs that
 * do not address a negotiation template of the expected phase, phase-conclusion mismatch, missing required fields and
 * empty conditional content — fails with a standard {@link NullPointerException} (pure null arguments) or
 * {@link IllegalArgumentException} (blank, range and semantic-contract violations) that is not part of the SDK
 * processing-error hierarchy and carries an English message pointing at the offending input. No row ever reaches the
 * LLM. Structural URI malformation is impossible by construction of {@link TemplateUri} and is therefore not a row.
 */
class FromDataProgrammingErrorMatrixTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final TemplateUri INFORMATION_ACCEPT_URI = StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT;

    private static final TemplateUri TARGET_PROPOSE_URI = StandardTemplates.TARGET_NEGOTIATION_PROPOSE;

    private static final TemplateUri FEASIBILITY_PROPOSE_URI = StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE;

    private static final TemplateUri FEASIBILITY_ACCEPT_URI = StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT;

    private static final TemplateUri TARGET_ACCEPT_URI = StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT;

    private final CountingClient llm = new CountingClient();

    private final NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
            .language("zh-CN")
            .llmClient(llm)
            .build();

    @Test
    void everyMatrixRowFailsWithAStandardJavaException() {
        List<MatrixRow> matrix = matrix();

        // The propose-versus-ending family mismatch of UT-GEN-002 is deliberately absent here: the typed data records
        // make that mismatch a compile-time error at the facade, so it cannot occur at runtime.
        assertEquals(22, matrix.size(), "the full matrix must be exercised");
        for (MatrixRow row : matrix) {
            RuntimeException failure = assertThrows(
                    row.expectedException(), () -> row.call().get(), "row must fail: " + row.label());
            assertFalse(
                    A2ATError.class.isInstance(failure),
                    "a programming error must not be part of the processing-error hierarchy: " + row.label());
            assertTrue(
                    failure.getMessage() != null && !failure.getMessage().isBlank(),
                    "failure message must be present: " + row.label());
            assertTrue(
                    isAsciiText(failure.getMessage()),
                    "failure message must be English (ASCII): " + failure.getMessage());
            assertTrue(
                    failure.getMessage().contains(row.expectedMessageFragment()),
                    "failure message must point at the problem of row " + row.label() + " but was: "
                            + failure.getMessage());
        }
        assertEquals(0, llm.calls, "no matrix row may call the LLM");
    }

    /** Returns the full programming-error matrix of the from-data variants. */
    private List<MatrixRow> matrix() {
        NegotiationContext context = new NegotiationContext(UUID, 2, 5);
        return List.of(
                new MatrixRow(
                        "null propose data",
                        () -> orchestrator.generateProposeFromData(null, INFORMATION_PROPOSE_URI),
                        NullPointerException.class,
                        "Negotiation propose data must not be null."),
                new MatrixRow(
                        "null ending data",
                        () -> orchestrator.generateAcceptFromData(null, INFORMATION_ACCEPT_URI),
                        NullPointerException.class,
                        "Negotiation ending data must not be null."),
                new MatrixRow(
                        "null context",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(null, informationProposeContent()), INFORMATION_PROPOSE_URI),
                        NullPointerException.class,
                        "Negotiation context must not be null."),
                new MatrixRow(
                        "accept method with reject conclusion",
                        () -> orchestrator.generateAcceptFromData(
                                ending(NegotiationConclusion.REJECT), INFORMATION_ACCEPT_URI),
                        IllegalArgumentException.class,
                        "ACCEPT phase requires conclusion Accept but the content carries Reject"),
                new MatrixRow(
                        "reject method with accept conclusion",
                        () -> orchestrator.generateRejectFromData(
                                ending(NegotiationConclusion.ACCEPT), INFORMATION_ACCEPT_URI),
                        IllegalArgumentException.class,
                        "REJECT phase requires conclusion Reject but the content carries Accept"),
                new MatrixRow(
                        "accept method with abort conclusion",
                        () -> orchestrator.generateAcceptFromData(
                                ending(NegotiationConclusion.ABORT), INFORMATION_ACCEPT_URI),
                        IllegalArgumentException.class,
                        "the content carries Abort"),
                new MatrixRow(
                        "accept method with null conclusion",
                        () -> orchestrator.generateAcceptFromData(
                                new NegotiationEndingData(context, new TargetEndingContent(null, "intent", null)),
                                TARGET_ACCEPT_URI),
                        NullPointerException.class,
                        "conclusion must not be null"),
                new MatrixRow(
                        "target accept without confirmed intent",
                        () -> orchestrator.generateAcceptFromData(
                                new NegotiationEndingData(
                                        context, new TargetEndingContent(NegotiationConclusion.ACCEPT, null, null)),
                                TARGET_ACCEPT_URI),
                        IllegalArgumentException.class,
                        "Confirmed intent of an accepting target negotiation message must not be blank."),
                new MatrixRow(
                        "target accept with blank confirmed intent",
                        () -> orchestrator.generateAcceptFromData(
                                new NegotiationEndingData(
                                        context, new TargetEndingContent(NegotiationConclusion.ACCEPT, "   ", null)),
                                TARGET_ACCEPT_URI),
                        IllegalArgumentException.class,
                        "Confirmed intent of an accepting target negotiation message must not be blank."),
                new MatrixRow(
                        "target reject without failure reason",
                        () -> orchestrator.generateRejectFromData(
                                new NegotiationEndingData(
                                        context, new TargetEndingContent(NegotiationConclusion.REJECT, null, null)),
                                TARGET_ACCEPT_URI),
                        IllegalArgumentException.class,
                        "Failure reason of a rejecting target negotiation message must not be blank."),
                new MatrixRow(
                        "target propose with blank description",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(context, new TargetProposeContent(" ", null, null, null)),
                                TARGET_PROPOSE_URI),
                        IllegalArgumentException.class,
                        "Target negotiation description must not be blank."),
                new MatrixRow(
                        "feasibility propose with blank description",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(
                                        context,
                                        new FeasibilityProposeContent(
                                                " ",
                                                NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                                                List.of(new NegotiationItem("目标", "2Mbps")),
                                                null)),
                                FEASIBILITY_PROPOSE_URI),
                        IllegalArgumentException.class,
                        "Feasibility negotiation description must not be blank."),
                new MatrixRow(
                        "feasibility propose without action",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(
                                        context,
                                        new FeasibilityProposeContent(
                                                "请评估。", null, List.of(new NegotiationItem("目标", "2Mbps")), null)),
                                FEASIBILITY_PROPOSE_URI),
                        NullPointerException.class,
                        "Feasibility negotiation action must not be null"),
                new MatrixRow(
                        "evaluation request without contents to evaluate",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(
                                        context,
                                        new FeasibilityProposeContent(
                                                "请评估。", NegotiationAction.REQUEST_FEASIBILITY_EVALUATION, null, null)),
                                FEASIBILITY_PROPOSE_URI),
                        IllegalArgumentException.class,
                        "must contain at least one item"),
                new MatrixRow(
                        "alternative proposal without details",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(
                                        context,
                                        new FeasibilityProposeContent(
                                                "不可行。", NegotiationAction.PROPOSE_ALTERNATIVE_ON_FAILURE, null, null)),
                                FEASIBILITY_PROPOSE_URI),
                        IllegalArgumentException.class,
                        "must contain at least one item"),
                new MatrixRow(
                        "feasibility ending with blank summary",
                        () -> orchestrator.generateAcceptFromData(
                                new NegotiationEndingData(
                                        context, new FeasibilityEndingContent(NegotiationConclusion.ACCEPT, " ")),
                                FEASIBILITY_ACCEPT_URI),
                        IllegalArgumentException.class,
                        "must not be blank"),
                new MatrixRow(
                        "template URI with wrong prefix",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(context, informationProposeContent()),
                                TemplateUri.of("Task-T", "v1", "information-negotiation", "propose")),
                        IllegalArgumentException.class,
                        "Template URI does not address a negotiation template of the expected phase PROPOSE (propose)"),
                new MatrixRow(
                        "template URI with wrong version",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(context, informationProposeContent()),
                                TemplateUri.of("Negotiation-T", "v2", "information-negotiation", "propose")),
                        IllegalArgumentException.class,
                        "Template URI does not address a negotiation template of the expected phase PROPOSE (propose)"),
                new MatrixRow(
                        "template URI with underscore type segment",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(context, informationProposeContent()),
                                TemplateUri.of("Negotiation-T", "v1", "information_negotiation", "propose")),
                        IllegalArgumentException.class,
                        "Template URI does not address a negotiation template of the expected phase PROPOSE (propose)"),
                new MatrixRow(
                        "template URI with unknown type",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(context, informationProposeContent()),
                                TemplateUri.of("Negotiation-T", "v1", "unknown-negotiation", "propose")),
                        IllegalArgumentException.class,
                        "Template URI does not address a negotiation template of the expected phase PROPOSE (propose)"),
                new MatrixRow(
                        "template URI phase contradicts the method",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(context, informationProposeContent()),
                                INFORMATION_ACCEPT_URI),
                        IllegalArgumentException.class,
                        "Template URI does not address a negotiation template of the expected phase PROPOSE (propose)"),
                new MatrixRow(
                        "template URI type contradicts the content type",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(
                                        context, new TargetProposeContent("目标协商概述。", null, null, null)),
                                INFORMATION_PROPOSE_URI),
                        IllegalArgumentException.class,
                        "Negotiation type INFORMATION requires content of type InfoProposeContent"));
    }

    private static InfoProposeContent informationProposeContent() {
        return new InfoProposeContent(List.of(new NegotiationItem("区域", "松山湖")), null);
    }

    private static NegotiationEndingData ending(NegotiationConclusion conclusion) {
        return new NegotiationEndingData(
                new NegotiationContext(UUID, 2, 5),
                new InfoEndingContent(conclusion, List.of(new NegotiationItem("区域", "松山湖"))));
    }

    private static boolean isAsciiText(String message) {
        return message.chars().allMatch(codePoint -> codePoint < 128);
    }

    /** One row of the programming-error matrix: a failing call, the expected exception type and message fragment. */
    private record MatrixRow(
            String label, Supplier<Object> call, Class<? extends RuntimeException> expectedException, String expectedMessageFragment) {}

    private static final class CountingClient implements LLMClient {

        private int calls;

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            calls++;
            throw new AssertionError("A from-data programming error must fail before any LLM call");
        }
    }
}
