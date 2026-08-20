package net.openan.a2at.sdk.negotiation.resources;

import java.util.List;
import net.openan.a2at.sdk.core.model.PromptTemplate;

/**
 * Loads negotiation templates addressed by {@link NegotiationReference} keys.
 *
 * @since 2026-06
 */
public interface NegotiationTemplateLoader {

    /**
     * Loads one negotiation template.
     *
     * @param reference template addressing key, including the language to load
     * @return loaded template with its URI, description and full content
     * @throws net.openan.a2at.sdk.core.exception.ResourceNotFoundException if no template exists for the reference in
     *     any available resource root
     */
    PromptTemplate load(NegotiationReference reference);

    /**
     * Loads every loadable negotiation template of the loader's language.
     *
     * <p>Templates that do not exist are skipped; the result order is fixed by the negotiation type order followed by
     * the phase order.
     *
     * @return templates of the loader's language that could be loaded, in a fixed order
     */
    List<PromptTemplate> loadAll();
}