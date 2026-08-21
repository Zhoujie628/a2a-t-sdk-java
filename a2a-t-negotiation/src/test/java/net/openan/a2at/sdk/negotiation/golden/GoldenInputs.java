package net.openan.a2at.sdk.negotiation.golden;

import java.util.List;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InfoEndingContent;
import net.openan.a2at.sdk.negotiation.content.InfoProposeContent;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;

/**
 * Fixed inputs of the golden fixture set of the negotiation content layer.
 *
 * <p>Every golden fixture is rendered from exactly one {@link GoldenCase} carrying a fixed negotiation context, fixed
 * typed content and the built-in template URI of its negotiation type and phase. The same content data is used for
 * every language: only the vocabulary rendering (section titles, labels, list punctuation) differs between zh-CN and
 * en-US.
 *
 * <p>The fixture data deliberately exercises the known rendering pitfalls: a non-null relationship with an appended
 * line and a null-value item (information propose), the round-driven conditional sections with a non-empty
 * clarification list (target propose, round 1), the action-driven conditional section (feasibility propose, evaluation
 * request action), the accept and reject conclusion literals, and the feasibility summary rendered into the
 * vocabulary-exception slot of the feasibility result confirmation section.
 */
public final class GoldenInputs {

    /** Fixed negotiation session id shared by every golden fixture. */
    public static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    /** Language with bundled zh-CN negotiation resources. */
    public static final String ZH_CN = "zh-CN";

    /** Language with bundled en-US negotiation resources. */
    public static final String EN_US = "en-US";

    /** Both languages covered by the golden fixture set, in a fixed order. */
    public static final List<String> LANGUAGES = List.of(ZH_CN, EN_US);

    private GoldenInputs() {}

    /**
     * Returns the default fixture context: round 2 of at most 5 rounds.
     *
     * @return fixed negotiation context of every fixture except the target propose fixture
     */
    public static NegotiationContext defaultContext() {
        return new NegotiationContext(SESSION_ID, 2, 5);
    }

    /**
     * Returns the first-round fixture context used by the target propose fixture.
     *
     * @return fixed negotiation context of round 1 of at most 5 rounds
     */
    public static NegotiationContext firstRoundContext() {
        return new NegotiationContext(SESSION_ID, 1, 5);
    }

    /** One golden fixture case: one negotiation type, phase, fixed context, fixed content and its template URI. */
    public enum GoldenCase {

        /** Information propose fixture with three items, one null-value item and a non-null relationship. */
        INFORMATION_PROPOSE(NegotiationPhase.PROPOSE, "information-negotiation", "information_propose") {
            @Override
            public NegotiationContext context() {
                return GoldenInputs.defaultContext();
            }

            @Override
            public NegotiationContent content() {
                return new InfoProposeContent(
                        List.of(
                                new NegotiationItem("energy-saving area information", "e.g. Songshan Lake"),
                                new NegotiationItem("energy-saving rate guarantee target", "e.g. 20Mbps"),
                                new NegotiationItem("VLANId", null)),
                        "OR");
            }
        },

        /** Target propose fixture of round 1 with intent understanding and a non-empty clarification request. */
        TARGET_PROPOSE(NegotiationPhase.PROPOSE, "target-negotiation", "target_propose") {
            @Override
            public NegotiationContext context() {
                return GoldenInputs.firstRoundContext();
            }

            @Override
            public NegotiationContent content() {
                return new TargetProposeContent(
                        "The intent understanding of the wireless energy-saving optimization task is listed in"
                                + " <Intent Understanding Statement>; open questions remain about the area and the"
                                + " time range, see <Content to Clarify>; please clarify and confirm.",
                        List.of(
                                new NegotiationItem(
                                        "task intent",
                                        "apply wireless energy-saving optimization to the target site during"
                                                + " 08:00-18:00"),
                                new NegotiationItem(
                                        "rate guarantee target", "at least 20Mbps during the energy-saving period")),
                        null,
                        List.of(
                                new NegotiationItem("area", "which site is covered: Songshan Lake or another site"),
                                new NegotiationItem(
                                        "time range", "is the energy-saving period 08:00-18:00 or 00:00-06:00")));
            }
        },

        /** Feasibility propose fixture requesting a feasibility evaluation of an adjusted rate target. */
        FEASIBILITY_PROPOSE(NegotiationPhase.PROPOSE, "feasibility-negotiation", "feasibility_propose") {
            @Override
            public NegotiationContext context() {
                return GoldenInputs.defaultContext();
            }

            @Override
            public NegotiationContent content() {
                return new FeasibilityProposeContent(
                        "Please assess whether the adjusted rate guarantee target of the site-level energy-saving"
                                + " task can be achieved, see <Under Evaluation Description>; please assess.",
                        NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                        List.of(
                                new NegotiationItem(
                                        "adjusted target",
                                        "rate guarantee target lowered from 5Mbps to 2Mbps during 08:00-18:00"),
                                new NegotiationItem(
                                        "existing constraint",
                                        "at least 10 hours of power supply duration in outage scenarios")),
                        null);
            }
        },

        /** Information accept fixture delivering the requested information items. */
        INFORMATION_ACCEPT(NegotiationPhase.ACCEPT, "information-negotiation", "information_accept") {
            @Override
            public NegotiationContent content() {
                return new InfoEndingContent(
                        NegotiationConclusion.ACCEPT,
                        List.of(
                                new NegotiationItem("energy-saving area information", "Songshan Lake"),
                                new NegotiationItem("energy-saving rate guarantee target", "20Mbps")));
            }
        },

        /** Target accept fixture carrying the finally confirmed intent. */
        TARGET_ACCEPT(NegotiationPhase.ACCEPT, "target-negotiation", "target_accept") {
            @Override
            public NegotiationContent content() {
                return new TargetEndingContent(
                        NegotiationConclusion.ACCEPT,
                        "The finally confirmed intent: apply wireless energy-saving optimization to the Songshan"
                                + " Lake site during 08:00-18:00 with a guaranteed rate of at least 20Mbps.",
                        null);
            }
        },

        /** Feasibility accept fixture confirming a positive evaluation result in the exception slot. */
        FEASIBILITY_ACCEPT(NegotiationPhase.ACCEPT, "feasibility-negotiation", "feasibility_accept") {
            @Override
            public NegotiationContent content() {
                return new FeasibilityEndingContent(
                        NegotiationConclusion.ACCEPT,
                        "The adjusted rate guarantee target is achievable and satisfies the outage duration"
                                + " requirement; this negotiation is confirmed as concluded.");
            }
        },

        /** Information reject fixture stating that an item cannot be provided. */
        INFORMATION_REJECT(NegotiationPhase.REJECT, "information-negotiation", "information_reject") {
            @Override
            public NegotiationContent content() {
                return new InfoEndingContent(
                        NegotiationConclusion.REJECT,
                        List.of(new NegotiationItem(
                                "energy-saving area information",
                                "cannot be provided because the site inventory is unavailable")));
            }
        },

        /** Target reject fixture carrying the failure reason. */
        TARGET_REJECT(NegotiationPhase.REJECT, "target-negotiation", "target_reject") {
            @Override
            public NegotiationContent content() {
                return new TargetEndingContent(
                        NegotiationConclusion.REJECT,
                        null,
                        "The area information cannot be clarified in full because the site inventory is"
                                + " unavailable.");
            }
        },

        /** Feasibility reject fixture confirming a negative evaluation result in the exception slot. */
        FEASIBILITY_REJECT(NegotiationPhase.REJECT, "feasibility-negotiation", "feasibility_reject") {
            @Override
            public NegotiationContent content() {
                return new FeasibilityEndingContent(
                        NegotiationConclusion.REJECT,
                        "The energy-saving target cannot be achieved under the existing power supply constraint;"
                                + " this negotiation is confirmed as concluded.");
            }
        };

        private final NegotiationPhase phase;

        private final String typeSegment;

        private final String fileName;

        GoldenCase(NegotiationPhase phase, String typeSegment, String fileName) {
            this.phase = phase;
            this.typeSegment = typeSegment;
            this.fileName = fileName;
        }

        /**
         * Returns the API-level phase of this fixture.
         *
         * @return propose, accept or reject phase
         */
        public NegotiationPhase phase() {
            return phase;
        }

        /**
         * Returns the built-in template URI addressed by this fixture.
         *
         * @return template URI such as {@code Negotiation-T/information-negotiation/propose/v1}
         */
        public String templateUri() {
            return "Negotiation-T/" + typeSegment + "/" + phase.uriSegment() + "/v1";
        }

        /**
         * Returns the golden fixture file name (without directory) of this fixture.
         *
         * @return file name such as {@code information_propose.md}
         */
        public String fileName() {
            return fileName + ".md";
        }

        /**
         * Returns the classpath path of the golden fixture of this case for one language.
         *
         * @param language locale identifier such as {@code zh-CN}
         * @return classpath path such as {@code /golden/zh-CN/information_propose.md}
         */
        public String goldenResourcePath(String language) {
            return "/golden/" + language + "/" + fileName();
        }

        /**
         * Returns the fixed negotiation context of this fixture.
         *
         * @return fixed negotiation context
         */
        public NegotiationContext context() {
            return GoldenInputs.defaultContext();
        }

        /**
         * Returns the fixed typed content of this fixture.
         *
         * @return fixed propose or ending content
         */
        public abstract NegotiationContent content();

        /**
         * Generates this fixture through one orchestrator, using the from-data method of the fixture phase.
         *
         * @param orchestrator orchestrator wired with the resources of the fixture language
         * @return generated metadata content of this fixture
         */
        public MetadataContent generate(NegotiationGenerationOrchestrator orchestrator) {
            return switch (phase) {
                case PROPOSE -> orchestrator.generateProposeFromData(
                        new NegotiationProposeData(context(), (NegotiationProposeContent) content()), templateUri());
                case ACCEPT -> orchestrator.generateAcceptFromData(
                        new NegotiationEndingData(context(), (NegotiationEndingContent) content()), templateUri());
                case REJECT -> orchestrator.generateRejectFromData(
                        new NegotiationEndingData(context(), (NegotiationEndingContent) content()), templateUri());
            };
        }
    }
}
