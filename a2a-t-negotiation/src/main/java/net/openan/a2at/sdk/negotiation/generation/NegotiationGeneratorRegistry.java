package net.openan.a2at.sdk.negotiation.generation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatch table from negotiation type and phase to the generator serving them.
 *
 * <p>Dispatch requires an exact runtime type match between the content and the addressed (type, phase) pair: propose
 * phases only accept propose content, terminal phases only accept ending content whose conclusion matches the phase,
 * and the content type must match the negotiation type. Subtype matching is deliberately not supported; new content
 * types must be registered explicitly.
 *
 * @since 2026-08
 */
final class NegotiationGeneratorRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(NegotiationGeneratorRegistry.class);

    private final Map<NegotiationType, NegotiationGenerator> proposeGenerators = new EnumMap<>(NegotiationType.class);

    private final Map<NegotiationType, NegotiationGenerator> endingGenerators = new EnumMap<>(NegotiationType.class);

    /** Creates a registry holding the six built-in negotiation generators. */
    public NegotiationGeneratorRegistry() {
        proposeGenerators.put(NegotiationType.INFORMATION, new InformationProposeGenerator());
        proposeGenerators.put(NegotiationType.TARGET, new TargetProposeGenerator());
        proposeGenerators.put(NegotiationType.FEASIBILITY, new FeasibilityProposeGenerator());
        endingGenerators.put(NegotiationType.INFORMATION, new InformationEndingGenerator());
        endingGenerators.put(NegotiationType.TARGET, new TargetEndingGenerator());
        endingGenerators.put(NegotiationType.FEASIBILITY, new FeasibilityEndingGenerator());
    }

    /**
     * Resolves the generator for one (type, phase, content) triple.
     *
     * @param type negotiation type addressed by the template URI
     * @param phase API-level phase addressed by the calling method
     * @param content typed content of the message
     * @return generator registered for the exact (type, phase) pair
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if the content family does not match the phase, the content runtime type does
     *     not match the negotiation type, or an ending content carries a conclusion that does not match the phase
     */
    public NegotiationGenerator resolve(NegotiationType type, NegotiationPhase phase, NegotiationContent content) {
        Objects.requireNonNull(type, "Negotiation type must not be null.");
        Objects.requireNonNull(phase, "Negotiation phase must not be null.");
        Objects.requireNonNull(content, "Negotiation content must not be null.");
        boolean proposePhase = phase == NegotiationPhase.PROPOSE;
        boolean proposeContent = content instanceof NegotiationProposeContent;
        if (proposePhase != proposeContent) {
            throw new IllegalArgumentException(
                    "The " + phase + " phase requires " + (proposePhase ? "propose" : "ending")
                            + " content but received "
                            + (proposeContent ? "propose" : "ending") + " content of type "
                            + content.getClass().getSimpleName() + ".");
        }
        Class<?> expectedType = expectedContentClass(type, proposePhase);
        if (content.getClass() != expectedType) {
            throw new IllegalArgumentException(
                    "Negotiation type " + type + " requires content of type " + expectedType.getSimpleName()
                            + " but received " + content.getClass().getSimpleName() + ".");
        }
        if (!proposePhase) {
            requireConclusionMatchesPhase((NegotiationEndingContent) content, phase);
        }
        NegotiationGenerator generator = (proposePhase ? proposeGenerators : endingGenerators).get(type);
        if (generator == null) {
            throw new IllegalArgumentException(
                    "No negotiation generator is registered for type " + type + " and phase " + phase + ".");
        }
        LOGGER.atDebug().log(
                "negotiation_generator_dispatched generator={} type={} phase={}",
                generator.getClass().getSimpleName(),
                type,
                phase);
        return generator;
    }

    private static void requireConclusionMatchesPhase(NegotiationEndingContent content, NegotiationPhase phase) {
        NegotiationConclusion conclusion = Objects.requireNonNull(
                content.conclusion(),
                "Negotiation conclusion must not be null; the " + phase + " phase requires a conclusion.");
        NegotiationConclusion expected =
                phase == NegotiationPhase.ACCEPT ? NegotiationConclusion.ACCEPT : NegotiationConclusion.REJECT;
        if (conclusion != expected) {
            throw new IllegalArgumentException(
                    "The " + phase + " phase requires conclusion " + expected.literal() + " but the content carries "
                            + conclusion.literal() + ".");
        }
    }

    private static Class<? extends NegotiationContent> expectedContentClass(
            NegotiationType type, boolean proposePhase) {
        if (proposePhase) {
            return switch (type) {
                case INFORMATION -> InformationProposeContent.class;
                case TARGET -> TargetProposeContent.class;
                case FEASIBILITY -> FeasibilityProposeContent.class;
            };
        }
        return switch (type) {
            case INFORMATION -> InformationEndingContent.class;
            case TARGET -> TargetEndingContent.class;
            case FEASIBILITY -> FeasibilityEndingContent.class;
        };
    }
}
