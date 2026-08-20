package net.openan.a2at.sample.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InfoEndingContent;
import net.openan.a2at.sdk.negotiation.content.InfoProposeContent;
import net.openan.a2at.sdk.negotiation.content.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.server.A2ATServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the output symmetry of the negotiation content layer across the client and the server facade.
 *
 * <p>Both facades expose the eleven negotiation API methods as one symmetric surface and produce, for identical inputs,
 * identical generation results: the whole MetadataContent record and the metadata map built from it are equal on both
 * sides. The inputs are the fixed golden fixture inputs of the negotiation module, so this test also proves that both
 * facades are wired to the same built-in resources.
 */
class NegotiationFacadeOutputSymmetryTest {

    private static final List<String> NEGOTIATION_API_METHODS = List.of(
            "generateNegotiationProposePromptFromData",
            "generateNegotiationAcceptPromptFromData",
            "generateNegotiationRejectPromptFromData",
            "generateNegotiationProposePromptFromText",
            "generateNegotiationAcceptPromptFromText",
            "generateNegotiationRejectPromptFromText",
            "getNegotiationPrompts",
            "getNegotiationPrompt",
            "validateAndFillingProposeData",
            "validateAndFillingAcceptData",
            "validateAndFillingRejectData");

    @TempDir
    Path tempDir;

    @Test
    void bothFacadesExposeTheSameElevenMethodSignatures() {
        Map<String, String> clientSurface = negotiationApiSurface(A2ATClient.class);
        Map<String, String> serverSurface = negotiationApiSurface(A2ATServer.class);

        assertEquals(NEGOTIATION_API_METHODS.stream().sorted().toList(), sortedKeys(clientSurface));
        assertEquals(clientSurface, serverSurface);
    }

    @Test
    void bothFacadesProduceIdenticalMetadataContentForTheSameInput() throws IOException {
        for (String language : List.of("zh-CN", "en-US")) {
            A2ATClient client = new A2ATClient(envFile(language, "client.env"));
            A2ATServer server = new A2ATServer(envFile(language, "server.env"));

            for (SymmetryCase symmetryCase : symmetryCases()) {
                MetadataContent clientResult = symmetryCase.generate(
                        client::generateNegotiationProposePromptFromData,
                        client::generateNegotiationAcceptPromptFromData,
                        client::generateNegotiationRejectPromptFromData);
                MetadataContent serverResult = symmetryCase.generate(
                        server::generateNegotiationProposePromptFromData,
                        server::generateNegotiationAcceptPromptFromData,
                        server::generateNegotiationRejectPromptFromData);

                assertNotNull(clientResult.promptText());
                assertEquals(clientResult, serverResult, "case " + symmetryCase.label() + " [" + language + "]");
                assertEquals(clientResult.buildMetadataContent(), serverResult.buildMetadataContent());
                assertEquals(symmetryCase.templateUri(), clientResult.templateUri());
            }
        }
    }

    private Path envFile(String language, String fileName) throws IOException {
        Path envFile = tempDir.resolve(language + "-" + fileName);
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=%s
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=local_rule
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        .formatted(language));
        return envFile;
    }

    private static List<String> sortedKeys(Map<String, String> surface) {
        return surface.keySet().stream().sorted().collect(Collectors.toList());
    }

    private static Map<String, String> negotiationApiSurface(Class<?> facade) {
        Map<String, String> surface = new LinkedHashMap<>();
        for (Method method : facade.getMethods()) {
            if (NEGOTIATION_API_METHODS.contains(method.getName())) {
                String parameters = Arrays.stream(method.getParameterTypes())
                        .map(Class::getName)
                        .collect(Collectors.joining(","));
                surface.put(
                        method.getName(),
                        parameters + "->" + method.getReturnType().getName());
            }
        }
        return surface;
    }

    /**
     * Builds the nine golden fixture cases of the negotiation module: one fixed typed content, context and template URI
     * per negotiation type and phase, identical on the client and the server side.
     */
    private static List<SymmetryCase> symmetryCases() {
        return List.of(
                new SymmetryCase(
                        "information_propose",
                        NegotiationPhase.PROPOSE,
                        "Negotiation-T/v1/information-negotiation/propose",
                        new NegotiationProposeData(
                                new NegotiationContext("3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 2, 5),
                                new InfoProposeContent(
                                        List.of(
                                                new NegotiationItem(
                                                        "energy-saving area information", "e.g. Songshan Lake"),
                                                new NegotiationItem("VLANId", null)),
                                        "OR"))),
                new SymmetryCase(
                        "target_propose",
                        NegotiationPhase.PROPOSE,
                        "Negotiation-T/v1/target-negotiation/propose",
                        new NegotiationProposeData(
                                new NegotiationContext("3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 1, 5),
                                new TargetProposeContent(
                                        "Clarify the intent of the energy-saving task.",
                                        List.of(new NegotiationItem("task intent", "energy-saving optimization")),
                                        null,
                                        List.of(new NegotiationItem("area", "which site is covered"))))),
                new SymmetryCase(
                        "feasibility_propose",
                        NegotiationPhase.PROPOSE,
                        "Negotiation-T/v1/feasibility-negotiation/propose",
                        new NegotiationProposeData(
                                new NegotiationContext("3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 2, 5),
                                new FeasibilityProposeContent(
                                        "Please assess the adjusted rate target.",
                                        NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                                        List.of(new NegotiationItem("adjusted target", "rate lowered to 2Mbps")),
                                        null))),
                endingCase(
                        "information_accept",
                        NegotiationPhase.ACCEPT,
                        "Negotiation-T/v1/information-negotiation/accept-reject",
                        new InfoEndingContent(
                                NegotiationConclusion.ACCEPT,
                                List.of(new NegotiationItem("area information", "Songshan Lake")))),
                endingCase(
                        "target_accept",
                        NegotiationPhase.ACCEPT,
                        "Negotiation-T/v1/target-negotiation/accept-reject",
                        new TargetEndingContent(NegotiationConclusion.ACCEPT, "The confirmed intent.", null)),
                endingCase(
                        "feasibility_accept",
                        NegotiationPhase.ACCEPT,
                        "Negotiation-T/v1/feasibility-negotiation/accept-reject",
                        new FeasibilityEndingContent(NegotiationConclusion.ACCEPT, "The target is achievable.")),
                endingCase(
                        "information_reject",
                        NegotiationPhase.REJECT,
                        "Negotiation-T/v1/information-negotiation/accept-reject",
                        new InfoEndingContent(
                                NegotiationConclusion.REJECT,
                                List.of(new NegotiationItem("area information", "not available")))),
                endingCase(
                        "target_reject",
                        NegotiationPhase.REJECT,
                        "Negotiation-T/v1/target-negotiation/accept-reject",
                        new TargetEndingContent(NegotiationConclusion.REJECT, null, "The intent is unclear.")),
                endingCase(
                        "feasibility_reject",
                        NegotiationPhase.REJECT,
                        "Negotiation-T/v1/feasibility-negotiation/accept-reject",
                        new FeasibilityEndingContent(NegotiationConclusion.REJECT, "The target is not achievable.")));
    }

    private static SymmetryCase endingCase(
            String label, NegotiationPhase phase, String templateUri, NegotiationEndingContent content) {
        return new SymmetryCase(
                label,
                phase,
                templateUri,
                new NegotiationEndingData(
                        new NegotiationContext("3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 2, 5), content));
    }

    /** One symmetry case: a fixed input addressed to one of the three from-data generation methods. */
    private record SymmetryCase(String label, NegotiationPhase phase, String templateUri, Object data) {

        MetadataContent generate(ProposeGenerator propose, EndingGenerator accept, EndingGenerator reject) {
            return switch (phase) {
                case PROPOSE -> propose.generate((NegotiationProposeData) data, templateUri);
                case ACCEPT -> accept.generate((NegotiationEndingData) data, templateUri);
                case REJECT -> reject.generate((NegotiationEndingData) data, templateUri);
            };
        }
    }

    @FunctionalInterface
    private interface ProposeGenerator {

        MetadataContent generate(NegotiationProposeData data, String templateUri);
    }

    @FunctionalInterface
    private interface EndingGenerator {

        MetadataContent generate(NegotiationEndingData data, String templateUri);
    }
}
