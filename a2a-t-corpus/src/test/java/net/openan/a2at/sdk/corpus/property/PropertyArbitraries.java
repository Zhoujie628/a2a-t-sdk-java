package net.openan.a2at.sdk.corpus.property;

import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Combinators;
import net.jqwik.api.Tuple;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeContent;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;

/**
 * Arbitraries of the jqwik property layer: legal negotiation inputs per design §8.3.
 *
 * <p>Session ids come from a fixed UUID pool so the validate-side rule gate always passes on the id dimension;
 * {@code round} is always within {@code [1, maxRounds]} and {@code maxRounds} within {@code [1, 10]}. Every typed
 * content arbitrary only produces shapes the generators accept: non-blank required descriptions, non-empty item lists
 * where the generator requires them, and null-or-non-empty optional lists elsewhere.
 *
 * @since 2026-08
 */
final class PropertyArbitraries {

    /** Fixed pool of rule-gate-valid session ids. */
    static final List<String> SESSION_ID_POOL = List.of(
            "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3",
            "0f5e9c1a-8b3d-4e7f-9a2c-6d1b4e8f0a53",
            "7c2a1e9b-4f6d-4a3b-8e5c-2f7a9d1b3e64",
            "1a8b3c5d-7e9f-4d2a-b6c4-e8f0a2d4b6c8",
            "9e7d5b3a-1c4f-4a8b-9d2e-6f8a0c2e4d6a",
            "5f3b7d9e-2a4c-4e6b-8a0d-3f5e7c9b1d38",
            "b9d1f3a5-7c2e-4b8d-9f1a-5e7c3b9d1f42",
            "e4c6a8b0-d2f4-4a9c-8e6b-2d0f4a6c8e10",
            "2b6d8f0a-c4e2-4a8c-b0d2-f4a6c8e0b2d4",
            "d8f0b2a4-6e8c-4a2b-8d0f-2b4d6f8a0c3e",
            "6a4c2e0b-8a0d-4f6c-9e1b-3d5f7a9c1e50",
            "0c2e4a6b-9d1f-4b3a-8f5c-7e9b1d3f5a72",
            "f1a3c5e7-2b4d-4f6a-8c0e-b2d4f6a8c0e2",
            "8e0b2d4f-6a8c-4e2b-9d1f-3a5c7e9b1d34",
            "4a6c8e0b-2d4f-4b8a-9c1e-5f7a3b9d1e56",
            "c5e7a9b1-d3f5-4a7c-8e0b-4d6f8a2c0e78");

    private static final List<String> LANGUAGES = List.of("zh-CN", "en-US");

    private static final List<String> ITEM_NAMES = List.of(
            "access_port",
            "complaint_category",
            "fault_time",
            "event_serial_no",
            "fault_detail",
            "private_line_id",
            "service_name",
            "access_vlan_id",
            "board_slot",
            "port_bandwidth",
            "latency_target",
            "packet_loss_rate");

    private static final List<String> ITEM_VALUES = List.of(
            "P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1",
            "P781-前海-PTN7900-5-TPA1EG24-09(cvlan=100)",
            "dedicated-line quality degradation",
            "private line interruption",
            "2026-05-11T08:21:46Z",
            "event-id-20260511-09013",
            "fault-id-1-017-20260516-11234",
            "within 20ms",
            "no higher than 1%",
            "within 48 hours");

    private static final List<String> NON_BLANK_TEXTS = List.of(
            "Private line complaint diagnosis negotiation for the Shenzhen-to-Guangzhou dedicated line",
            "Latency repair target negotiation after the quality degradation complaint",
            "Access port expansion feasibility assessment within the cutover window",
            "Complaint information supplement negotiation between the workbench and the OMC");

    /** Fixed wording pool of the confirm-request category, one entry per language. */
    private static final List<String> CONFIRM_REQUEST_TEXTS = List.of(
            "目标已经澄清，是否同意按照此目标继续执行？",
            "The target has been clarified. Do you agree to proceed with this target?",
            "目标评估为可行，是否同意按照此目标继续执行？",
            "The target is assessed as feasible. Do you agree to proceed with this target?");

    private PropertyArbitraries() {}

    /**
     * Arbitrary of the two bundled message languages.
     *
     * @return language arbitrary
     */
    static Arbitrary<String> languages() {
        return Arbitraries.of(LANGUAGES);
    }

    /**
     * Arbitrary of the four negotiation performatives.
     *
     * @return performative arbitrary
     */
    static Arbitrary<NegotiationPerformative> performatives() {
        return Arbitraries.of(NegotiationPerformative.values());
    }

    /**
     * Arbitrary of rule-gate-valid negotiation contexts: pooled UUID id, round in {@code [1, maxRounds]}, maxRounds in
     * {@code [1, 10]}, and any of the four performatives (the generation pipeline stamps the operation's performative
     * onto the emitted context, so the input performative is free).
     *
     * @return negotiation context arbitrary
     */
    static Arbitrary<NegotiationContext> contexts() {
        return Arbitraries.integers()
                .between(1, 10)
                .flatMap(maxRounds -> Arbitraries.integers()
                        .between(1, maxRounds)
                        .flatMap(round -> Arbitraries.of(SESSION_ID_POOL)
                                .flatMap(id -> performatives()
                                        .map(performative -> new NegotiationContext(
                                                id, round, maxRounds, performative)))));
    }

    /**
     * Arbitrary of single items whose value is null half of the time (the design's 50% null item value).
     *
     * @return item arbitrary
     */
    static Arbitrary<NegotiationItem> items() {
        Arbitrary<String> values = Arbitraries.frequencyOf(
                Tuple.of(1, Arbitraries.just(null)), Tuple.of(1, Arbitraries.of(ITEM_VALUES)));
        return Combinators.combine(Arbitraries.of(ITEM_NAMES), values).as(NegotiationItem::new);
    }

    /**
     * Arbitrary of item lists of the given size range.
     *
     * @param minSize minimum list size
     * @param maxSize maximum list size
     * @return item list arbitrary
     */
    static Arbitrary<List<NegotiationItem>> itemLists(int minSize, int maxSize) {
        return items().list().ofMinSize(minSize).ofMaxSize(maxSize);
    }

    /**
     * Arbitrary of optional item lists: null or a non-empty list (the design's 50% non-empty optional sections).
     *
     * @return optional item list arbitrary
     */
    static Arbitrary<List<NegotiationItem>> optionalItemLists() {
        return Arbitraries.frequencyOf(Tuple.of(1, Arbitraries.just(null)), Tuple.of(1, itemLists(1, 4)));
    }

    /**
     * Arbitrary of information propose contents: items in {@code [1, 5]} with 50% null item values, relationship null
     * or present. The information propose generator requires at least one requested item, so the empty list is not a
     * valid input.
     *
     * @return information propose content arbitrary
     */
    static Arbitrary<InformationProposeContent> informationProposeContents() {
        return Combinators.combine(itemLists(1, 5), optionalTexts()).as(InformationProposeContent::new);
    }

    /**
     * Arbitrary of target propose contents covering both message categories: the round-driven clarification rounds
     * (non-blank description, each optional list null or non-empty, no confirm request) and the confirm-request rounds
     * (non-blank description and confirm request with all three conditional lists null, so the generated record always
     * satisfies the mutual exclusion of the confirm-request category).
     *
     * @return target propose content arbitrary
     */
    static Arbitrary<TargetProposeContent> targetProposeContents() {
        return Arbitraries.frequencyOf(
                Tuple.of(2, clarificationRoundContents()), Tuple.of(1, confirmRoundTargetContents()));
    }

    private static Arbitrary<TargetProposeContent> clarificationRoundContents() {
        return Combinators.combine(
                        Arbitraries.of(NON_BLANK_TEXTS),
                        optionalItemLists(),
                        optionalItemLists(),
                        optionalItemLists())
                .as((description, intent, alignment, clarification) -> new TargetProposeContent(
                        description, intent, alignment, clarification, null));
    }

    private static Arbitrary<TargetProposeContent> confirmRoundTargetContents() {
        return Combinators.combine(
                        Arbitraries.of(NON_BLANK_TEXTS), Arbitraries.of(CONFIRM_REQUEST_TEXTS))
                .as((description, confirmRequest) -> new TargetProposeContent(
                        description, null, null, null, confirmRequest));
    }

    /**
     * Arbitrary of feasibility propose contents covering all three message categories: both action branches (the
     * action-selected item list always non-empty, the other one null or non-empty) and the derived confirm-request
     * category (REQUEST_FEASIBILITY_EVALUATION action with both lists null and a non-blank confirm request, which the
     * mutual exclusion of the category requires).
     *
     * @return feasibility propose content arbitrary
     */
    static Arbitrary<FeasibilityProposeContent> feasibilityProposeContents() {
        return Arbitraries.frequencyOf(
                Tuple.of(2, actionDrivenContents()), Tuple.of(1, confirmRoundFeasibilityContents()));
    }

    private static Arbitrary<FeasibilityProposeContent> actionDrivenContents() {
        return Combinators.combine(
                        Arbitraries.of(NegotiationAction.values()),
                        Arbitraries.of(NON_BLANK_TEXTS),
                        itemLists(1, 4),
                        optionalItemLists())
                .as((action, description, selected, other) -> new FeasibilityProposeContent(
                        description,
                        action,
                        action == NegotiationAction.REQUEST_FEASIBILITY_EVALUATION ? selected : other,
                        action == NegotiationAction.PROPOSE_ALTERNATIVE_ON_FAILURE ? selected : other,
                        null));
    }

    private static Arbitrary<FeasibilityProposeContent> confirmRoundFeasibilityContents() {
        return Combinators.combine(
                        Arbitraries.of(NON_BLANK_TEXTS), Arbitraries.of(CONFIRM_REQUEST_TEXTS))
                .as((description, confirmRequest) -> new FeasibilityProposeContent(
                        description,
                        NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                        null,
                        null,
                        confirmRequest));
    }

    /**
     * Arbitrary over the three propose content types.
     *
     * @return propose content arbitrary
     */
    static Arbitrary<NegotiationProposeContent> proposeContents() {
        return Arbitraries.oneOf(
                informationProposeContents(), targetProposeContents(), feasibilityProposeContents());
    }

    /**
     * Arbitrary of ending contents carrying the given conclusion across all three types.
     *
     * @param conclusion terminal conclusion the contents carry
     * @return ending content arbitrary
     */
    static Arbitrary<NegotiationEndingContent> endingContents(NegotiationConclusion conclusion) {
        Arbitrary<InformationEndingContent> information =
                itemLists(1, 4).map(items -> new InformationEndingContent(conclusion, items));
        Arbitrary<TargetEndingContent> target = Arbitraries.of(NON_BLANK_TEXTS)
                .map(text -> conclusion == NegotiationConclusion.ACCEPT
                        ? new TargetEndingContent(conclusion, text, null)
                        : new TargetEndingContent(conclusion, null, text));
        Arbitrary<FeasibilityEndingContent> feasibility =
                Arbitraries.of(NON_BLANK_TEXTS).map(text -> new FeasibilityEndingContent(conclusion, text));
        return Arbitraries.oneOf(information, target, feasibility);
    }

    /**
     * Arbitrary of abort contents with a non-blank termination reason.
     *
     * @return abort content arbitrary
     */
    static Arbitrary<NegotiationAbortContent> abortContents() {
        return Arbitraries.of(NON_BLANK_TEXTS).map(NegotiationAbortContent::new);
    }

    private static Arbitrary<String> optionalTexts() {
        return Arbitraries.frequencyOf(
                Tuple.of(1, Arbitraries.just(null)), Tuple.of(1, Arbitraries.of(ITEM_VALUES)));
    }

    /**
     * Picks one session id from the pool outside of the arbitrary layer.
     *
     * @return a fixed valid session id
     */
    static String anySessionId() {
        return SESSION_ID_POOL.get(0);
    }

    /**
     * Picks one language outside of the arbitrary layer.
     *
     * @return a fixed language
     */
    static String anyLanguage() {
        return LANGUAGES.get(0);
    }

    /**
     * Builds a fresh random session id; used where the pool semantics do not matter.
     *
     * @return a new UUID string
     */
    static String randomSessionId() {
        return UUID.randomUUID().toString();
    }
}
