package net.openan.a2at.sdk.negotiation.testdata;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The whole negotiation test corpus loaded from one corpus root.
 *
 * <p>The loader fails fast on any format violation, so a successfully loaded corpus is internally consistent: ids are
 * globally unique, every {@code $ref} is resolved, every expectation block is complete and every record is expanded
 * per language.
 *
 * @param root corpus root directory the records were loaded from
 * @param cases all case records of the case files, expanded per language
 * @param scenarios all scenario records of the scenario files, expanded per language
 * @param sharedResponses named payload texts of {@code shared/llm-responses.json}
 * @param sharedSchemas named JSON Schema variants of {@code shared/schemas.json}
 * @since 2026-08
 */
public record LoadedCorpus(
        Path root,
        List<NegotiationCase> cases,
        List<ScenarioCase> scenarios,
        Map<String, String> sharedResponses,
        Map<String, JsonNode> sharedSchemas) {

    public LoadedCorpus {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(cases, "cases");
        Objects.requireNonNull(scenarios, "scenarios");
        Objects.requireNonNull(sharedResponses, "sharedResponses");
        Objects.requireNonNull(sharedSchemas, "sharedSchemas");
        root = root.normalize();
        cases = List.copyOf(cases);
        scenarios = List.copyOf(scenarios);
        sharedResponses = Map.copyOf(sharedResponses);
        sharedSchemas = Map.copyOf(sharedSchemas);
    }
}
