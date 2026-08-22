package net.openan.a2at.sample.negotiation.shared;

import java.util.List;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.server.A2ATServer;

/**
 * LLM-based negotiation strategy: converts the items to natural-language text and calls the fromText API.
 *
 * <p>The SDK runs one LLM content-extraction step to parse the text into typed content, then renders deterministically.
 * This is the "natural language" path.
 *
 * @since 2026-08
 */
public final class FromTextStrategy implements NegotiationStrategy {

    @Override
    public MetadataContent generatePropose(
            A2ATServer server,
            NegotiationContext ctx,
            List<NegotiationItem> missingItems,
            String relationship,
            TemplateUri templateUri) {
        String text = itemsToProposeText(missingItems, relationship);
        return server.generateNegotiationProposePromptFromText(text, ctx, templateUri);
    }

    @Override
    public MetadataContent generateAccept(
            A2ATClient facade, NegotiationContext ctx, List<NegotiationItem> filledItems, TemplateUri templateUri) {
        String text = itemsToAcceptText(filledItems);
        return facade.generateNegotiationAcceptPromptFromText(text, ctx, templateUri);
    }

    @Override
    public MetadataContent generateAcceptServer(
            A2ATServer server, NegotiationContext ctx, List<NegotiationItem> filledItems, TemplateUri templateUri) {
        String text = itemsToAcceptText(filledItems);
        return server.generateNegotiationAcceptPromptFromText(text, ctx, templateUri);
    }

    private static String itemsToProposeText(List<NegotiationItem> items, String relationship) {
        StringBuilder sb = new StringBuilder("请提供以下缺失信息：");
        for (int i = 0; i < items.size(); i++) {
            NegotiationItem item = items.get(i);
            sb.append(i + 1).append(". ").append(item.name());
            if (item.value() != null && !item.value().isBlank()) {
                sb.append("：").append(item.value());
            }
            sb.append("；");
        }
        if (relationship != null && !relationship.isBlank()) {
            sb.append(relationship);
        }
        return sb.toString();
    }

    private static String itemsToAcceptText(List<NegotiationItem> items) {
        StringBuilder sb = new StringBuilder("同意补充以下信息：");
        for (int i = 0; i < items.size(); i++) {
            NegotiationItem item = items.get(i);
            sb.append(i + 1).append(". ").append(item.name());
            if (item.value() != null && !item.value().isBlank()) {
                sb.append("：").append(item.value());
            }
            sb.append("；");
        }
        sb.append("信息已完整，可以启动诊断。");
        return sb.toString();
    }
}
