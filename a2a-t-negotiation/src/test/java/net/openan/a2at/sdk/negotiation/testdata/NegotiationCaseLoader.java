package net.openan.a2at.sdk.negotiation.testdata;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.jspecify.annotations.Nullable;

/**
 * Strict loader of the negotiation test corpus.
 *
 * <p>The corpus lives under one root directory (the default is {@code src/test/resources/negotiation-cases}): case
 * files and scenario files are JSON arrays of records, and the {@code shared/} directory carries the two shared
 * reference files {@code llm-responses.json} (named payload texts, addressed through the {@code responses/} prefix)
 * and {@code schemas.json} (named JSON Schema variants, addressed through the {@code schemas/} prefix). The
 * {@code corpus-schema.json} format definition sits next to the records and is skipped by the loader.
 *
 * <p>Loading fails fast — before any case runs — on every format violation, and every error names the corpus file,
 * the offending record id and the JSON path of the defect:
 *
 * <ul>
 * <li>unknown keys (strict Jackson with {@code FAIL_ON_UNKNOWN_PROPERTIES} — a typo is an error, never a silent skip),
 * <li>dangling, out-of-scope, nested or circular {@code $ref} references,
 * <li>duplicate record ids (before and after the language expansion),
 * <li>incomplete expectation blocks (a failure expectation must name the exception or the error code),
 * <li>unknown API names, {@code $fail} markers, languages, priorities, terminal conditions or inject hooks,
 * <li>scenario steps that are not numbered consecutively from 1.
 * </ul>
 *
 * <p>On success every record is expanded once per entry of its {@code languages} array (Q3), the expanded id appending
 * {@code /<language>}, and references are resolved into literal payload texts and schema nodes, so the later case
 * engine never sees a reference.
 *
 * @since 2026-08
 */
public final class NegotiationCaseLoader {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("zh-CN", "en-US");

    private static final Set<String> SUPPORTED_PRIORITIES = Set.of("P0", "P1", "P2");

    private static final Set<String> SUPPORTED_TERMINAL_CONDITIONS = Set.of("accept", "reject", "abort", "exhausted");

    private static final String RESPONSES_PREFIX = "responses/";

    private static final String SCHEMAS_PREFIX = "schemas/";

    private static final String RESPONSES_FILE = "shared/llm-responses.json";

    private static final String SCHEMAS_FILE = "shared/schemas.json";

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private NegotiationCaseLoader() {}

    /**
     * Loads the whole corpus from a corpus root directory: every {@code .json} file except {@code corpus-schema.json}
     * and the {@code shared/} reference files.
     *
     * @param corpusRoot existing corpus root directory
     * @return the loaded corpus with all cases and scenarios expanded per language
     * @throws CorpusLoadException when any corpus record violates the format
     */
    public static LoadedCorpus load(Path corpusRoot) {
        Objects.requireNonNull(corpusRoot, "corpusRoot");
        if (!Files.isDirectory(corpusRoot)) {
            throw new CorpusLoadException("The corpus root is not an existing directory: " + corpusRoot);
        }
        Path normalizedRoot = corpusRoot.normalize();
        SharedRefs shared = loadShared(normalizedRoot.resolve("shared"));
        Map<String, String> seenBaseIds = new LinkedHashMap<>();
        Map<String, String> seenExpandedIds = new LinkedHashMap<>();
        List<NegotiationCase> cases = new ArrayList<>();
        List<ScenarioCase> scenarios = new ArrayList<>();
        for (Path file : listCorpusFiles(normalizedRoot)) {
            String relative = normalizedRoot.relativize(file.normalize()).toString().replace('\\', '/');
            parseFile(relative, file, shared, seenBaseIds, seenExpandedIds, cases, scenarios);
        }
        return new LoadedCorpus(
                normalizedRoot,
                cases,
                scenarios,
                toStringMap(shared.responses()),
                toNodeMap(shared.schemas()));
    }

    /**
     * Loads the whole corpus from a classpath directory, such as {@code negotiation-cases} of the test resources.
     *
     * @param resourcePath classpath path of the corpus root
     * @return the loaded corpus with all cases and scenarios expanded per language
     * @throws CorpusLoadException when the resource is missing or any corpus record violates the format
     */
    public static LoadedCorpus loadFromClasspath(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        URL url = NegotiationCaseLoader.class.getClassLoader().getResource(resourcePath);
        if (url == null) {
            throw new CorpusLoadException("The corpus root was not found on the classpath: " + resourcePath);
        }
        try {
            return load(Path.of(url.toURI()));
        } catch (URISyntaxException exception) {
            throw new CorpusLoadException("The corpus root resource cannot be addressed as a path: " + url, exception);
        }
    }

    // ------------------------------------------------------------------ file traversal

    private static List<Path> listCorpusFiles(Path corpusRoot) {
        Path sharedDir = corpusRoot.resolve("shared");
        try (Stream<Path> paths = Files.walk(corpusRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !path.normalize().startsWith(sharedDir))
                    .filter(path -> !"corpus-schema.json".equals(path.getFileName().toString()))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new CorpusLoadException("Failed to walk the corpus root: " + corpusRoot, exception);
        }
    }

    private record SharedRefs(JsonNode responses, JsonNode schemas) {}

    private static SharedRefs loadShared(Path sharedDir) {
        JsonNode responses = readSharedFile(sharedDir.resolve("llm-responses.json"));
        JsonNode schemas = readSharedFile(sharedDir.resolve("schemas.json"));
        responses.fieldNames().forEachRemaining(name -> {
            if (!responses.get(name).isTextual()) {
                throw error(RESPONSES_FILE, null, "$." + name, "every shared response payload must be a JSON string");
            }
        });
        schemas.fieldNames().forEachRemaining(name -> {
            if (!schemas.get(name).isObject()) {
                throw error(SCHEMAS_FILE, null, "$." + name, "every shared schema variant must be a JSON object");
            }
        });
        return new SharedRefs(responses, schemas);
    }

    private static JsonNode readSharedFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return MAPPER.createObjectNode();
        }
        try {
            JsonNode root = MAPPER.readTree(file.toFile());
            if (!root.isObject()) {
                throw error(
                        "shared/" + file.getFileName(),
                        null,
                        "$",
                        "expected a JSON object mapping names to shared values");
            }
            return root;
        } catch (IOException exception) {
            throw new CorpusLoadException("Failed to read the shared corpus file: " + file, exception);
        }
    }

    private static void parseFile(
            String file,
            Path path,
            SharedRefs shared,
            Map<String, String> seenBaseIds,
            Map<String, String> seenExpandedIds,
            List<NegotiationCase> cases,
            List<ScenarioCase> scenarios) {
        JsonNode root;
        try {
            root = MAPPER.readTree(path.toFile());
        } catch (IOException exception) {
            throw new CorpusLoadException(file + ": failed to read the corpus file", exception);
        }
        if (!root.isArray()) {
            throw error(file, null, "$", "expected a JSON array of corpus records");
        }
        for (int i = 0; i < root.size(); i++) {
            JsonNode node = root.get(i);
            String recordPath = "$[" + i + "]";
            if (!node.isObject()) {
                throw error(file, null, recordPath, "expected a JSON object record");
            }
            if (!node.path("id").isTextual()) {
                throw error(file, null, recordPath + ".id", "missing required field 'id'");
            }
            String id = node.get("id").asText();
            if (node.has("steps")) {
                parseScenario(file, id, recordPath, node, shared, seenBaseIds, seenExpandedIds, scenarios);
            } else {
                parseCase(file, id, recordPath, node, shared, seenBaseIds, seenExpandedIds, cases);
            }
        }
    }

    // ------------------------------------------------------------------ record parsing

    private static void parseCase(
            String file,
            String id,
            String path,
            JsonNode node,
            SharedRefs shared,
            Map<String, String> seenBaseIds,
            Map<String, String> seenExpandedIds,
            List<NegotiationCase> cases) {
        RawCase raw = bind(node, RawCase.class, file, id, path);
        List<String> languages = validateLanguages(raw.languages(), file, id, path);
        claimBaseId(id, file, path, seenBaseIds);
        String priority = validatePriority(raw.priority(), file, id, path);
        List<String> tags = raw.tags() == null ? List.of() : List.copyOf(raw.tags());
        ResolvedFields resolved = resolveCaseFields(
                file,
                id,
                path,
                languages,
                raw.api(),
                raw.context(),
                raw.templateUri(),
                raw.input(),
                raw.llm(),
                raw.prompt(),
                raw.schema(),
                raw.inject(),
                raw.expect(),
                shared);
        for (String language : languages) {
            String expandedId = id + "/" + language;
            claimExpandedId(expandedId, file, id, path, seenExpandedIds);
            cases.add(new NegotiationCase(
                    expandedId,
                    id,
                    file,
                    resolved.api(),
                    language,
                    priority,
                    tags,
                    raw.summary(),
                    resolved.context(),
                    resolved.templateUri(),
                    resolved.inputText().get(language),
                    resolved.inputData(),
                    resolved.llm(),
                    resolved.prompt(),
                    resolved.schema(),
                    resolved.inject(),
                    resolved.expect()));
        }
    }

    private static void parseScenario(
            String file,
            String id,
            String path,
            JsonNode node,
            SharedRefs shared,
            Map<String, String> seenBaseIds,
            Map<String, String> seenExpandedIds,
            List<ScenarioCase> scenarios) {
        RawScenario raw = bind(node, RawScenario.class, file, id, path);
        List<String> languages = validateLanguages(raw.languages(), file, id, path);
        claimBaseId(id, file, path, seenBaseIds);
        List<String> roles = raw.roles() == null ? List.of() : List.copyOf(raw.roles());
        if (roles.stream().anyMatch(String::isBlank)) {
            throw error(file, id, path + ".roles", "role names must not be blank");
        }
        if (raw.steps() == null || raw.steps().isEmpty()) {
            throw error(file, id, path + ".steps", "missing required field 'steps' (at least one step)");
        }
        for (int i = 0; i < raw.steps().size(); i++) {
            Integer stepNumber = raw.steps().get(i).step();
            if (stepNumber == null || stepNumber != i + 1) {
                throw error(
                        file,
                        id,
                        path + ".steps[" + i + "].step",
                        "scenario steps must be numbered consecutively from 1, but step " + (i + 1) + " carries '"
                                + stepNumber + "'");
            }
        }
        ScenarioCase.ExpectFlow expectFlow = buildExpectFlow(raw.expectFlow(), file, id, path);
        for (String language : languages) {
            String expandedId = id + "/" + language;
            claimExpandedId(expandedId, file, id, path, seenExpandedIds);
            List<ScenarioCase.ScenarioStep> steps = new ArrayList<>();
            for (RawStep rawStep : raw.steps()) {
                ResolvedFields resolved = resolveCaseFields(
                        file,
                        id,
                        path + ".steps[" + (rawStep.step() - 1) + "]",
                        languages,
                        rawStep.api(),
                        rawStep.context(),
                        rawStep.templateUri(),
                        rawStep.input(),
                        rawStep.llm(),
                        rawStep.prompt(),
                        rawStep.schema(),
                        rawStep.inject(),
                        rawStep.expect(),
                        shared);
                NegotiationCase stepCase = new NegotiationCase(
                        expandedId + "#step-" + rawStep.step(),
                        id,
                        file,
                        resolved.api(),
                        language,
                        null,
                        List.of(),
                        null,
                        resolved.context(),
                        resolved.templateUri(),
                        resolved.inputText().get(language),
                        resolved.inputData(),
                        resolved.llm(),
                        resolved.prompt(),
                        resolved.schema(),
                        resolved.inject(),
                        resolved.expect());
                steps.add(new ScenarioCase.ScenarioStep(rawStep.step(), rawStep.role(), stepCase));
            }
            scenarios.add(new ScenarioCase(expandedId, id, file, language, raw.summary(), roles, steps, expectFlow));
        }
    }

    private record ResolvedFields(
            NegotiationApi api,
            @Nullable ContextSpec context,
            @Nullable String templateUri,
            Map<String, String> inputText,
            @Nullable JsonNode inputData,
            @Nullable LlmScript llm,
            @Nullable PromptSource prompt,
            @Nullable JsonNode schema,
            @Nullable String inject,
            Expectation expect) {}

    private static ResolvedFields resolveCaseFields(
            String file,
            String id,
            String path,
            List<String> languages,
            @Nullable String apiName,
            @Nullable RawContext context,
            @Nullable String templateUri,
            @Nullable RawInput input,
            @Nullable RawLlm llm,
            @Nullable RawPrompt prompt,
            @Nullable JsonNode schema,
            @Nullable String inject,
            @Nullable RawExpect expect,
            SharedRefs shared) {
        NegotiationApi api = NegotiationApi.fromJsonName(apiName);
        if (api == null) {
            throw error(
                    file,
                    id,
                    path + ".api",
                    "unknown api '" + apiName + "' (known apis: " + knownApiNames() + ")");
        }
        ContextSpec contextSpec = validateContext(context, file, id, path);
        if (templateUri != null && templateUri.isBlank()) {
            throw error(file, id, path + ".templateUri", "the template URI must not be blank");
        }
        Map<String, String> inputText = Map.of();
        JsonNode inputData = null;
        if (input != null) {
            if (input.text() != null) {
                inputText = input.text();
                for (String language : languages) {
                    if (!inputText.containsKey(language)) {
                        throw error(
                                file,
                                id,
                                path + ".input.text",
                                "missing the '" + language
                                        + "' text entry (the input must cover every language of the record)");
                    }
                }
            }
            if (input.data() != null && !input.data().isNull()) {
                if (!input.data().isObject()) {
                    throw error(file, id, path + ".input.data", "the typed input data must be a JSON object");
                }
                inputData = input.data();
            }
        }
        LlmScript llmScript = null;
        if (llm != null) {
            if (llm.maxAttempts() != null && llm.maxAttempts() < 1) {
                throw error(file, id, path + ".llm.maxAttempts", "maxAttempts must be at least 1");
            }
            if (llm.script() == null || llm.script().isEmpty()) {
                throw error(file, id, path + ".llm.script", "missing required field 'script' (at least one step)");
            }
            llmScript =
                    new LlmScript(llm.maxAttempts(), resolveScript(llm.script(), file, id, path + ".llm.script", shared));
        }
        PromptSource promptSource = buildPromptSource(prompt, file, id, path);
        JsonNode schemaNode = resolveSchema(schema, file, id, path, shared);
        if (inject != null
                && !"failingTemplateLoader".equals(inject)
                && !"failingSemanticValidator".equals(inject)) {
            throw error(
                    file,
                    id,
                    path + ".inject",
                    "unknown inject hook '" + inject
                            + "' (known hooks: failingTemplateLoader, failingSemanticValidator)");
        }
        Expectation expectation = buildExpectation(expect, file, id, path);
        return new ResolvedFields(
                api,
                contextSpec,
                templateUri,
                inputText,
                inputData,
                llmScript,
                promptSource,
                schemaNode,
                inject,
                expectation);
    }

    // ------------------------------------------------------------------ field validation

    private static List<String> validateLanguages(@Nullable List<String> languages, String file, String id, String path) {
        if (languages == null || languages.isEmpty()) {
            throw error(file, id, path + ".languages", "missing required field 'languages' (at least one language)");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String language : languages) {
            if (!seen.add(language)) {
                throw error(file, id, path + ".languages", "duplicate language '" + language + "'");
            }
            if (!SUPPORTED_LANGUAGES.contains(language)) {
                throw error(
                        file,
                        id,
                        path + ".languages",
                        "unsupported language '" + language + "' (supported languages: zh-CN, en-US)");
            }
        }
        return List.copyOf(languages);
    }

    private static @Nullable String validatePriority(@Nullable String priority, String file, String id, String path) {
        if (priority != null && !SUPPORTED_PRIORITIES.contains(priority)) {
            throw error(file, id, path + ".priority", "unknown priority '" + priority + "' (known priorities: P0, P1, P2)");
        }
        return priority;
    }

    private static @Nullable ContextSpec validateContext(
            @Nullable RawContext context, String file, String id, String path) {
        if (context == null) {
            return null;
        }
        if (context.id() == null || context.id().isBlank()) {
            throw error(file, id, path + ".context.id", "the context id must not be blank");
        }
        if (context.round() == null) {
            throw error(file, id, path + ".context.round", "missing required field 'round'");
        }
        if (context.maxRounds() == null) {
            throw error(file, id, path + ".context.maxRounds", "missing required field 'maxRounds'");
        }
        if (context.round() < 1 || context.maxRounds() < 1) {
            throw error(file, id, path + ".context", "the context round and maxRounds must be at least 1");
        }
        return new ContextSpec(context.id(), context.round(), context.maxRounds());
    }

    private static @Nullable PromptSource buildPromptSource(
            @Nullable RawPrompt prompt, String file, String id, String path) {
        if (prompt == null) {
            return null;
        }
        int declared = 0;
        if (prompt.golden() != null) {
            declared++;
        }
        if (prompt.text() != null) {
            declared++;
        }
        if (prompt.fromStep() != null) {
            declared++;
        }
        if (declared != 1) {
            throw error(
                    file,
                    id,
                    path + ".prompt",
                    "the prompt must declare exactly one of golden, text or fromStep but declares " + declared);
        }
        if (prompt.golden() != null) {
            if (prompt.golden().isBlank()) {
                throw error(file, id, path + ".prompt.golden", "the golden fixture name must not be blank");
            }
            return new PromptSource.Golden(prompt.golden());
        }
        if (prompt.text() != null) {
            return new PromptSource.Text(prompt.text());
        }
        if (prompt.fromStep() < 1) {
            throw error(file, id, path + ".prompt.fromStep", "fromStep must be at least 1");
        }
        return new PromptSource.FromStep(prompt.fromStep());
    }

    private static Expectation buildExpectation(@Nullable RawExpect expect, String file, String id, String path) {
        if (expect == null) {
            throw error(file, id, path + ".expect", "missing required field 'expect'");
        }
        boolean success;
        if ("success".equals(expect.outcome())) {
            success = true;
        } else if ("failure".equals(expect.outcome())) {
            success = false;
        } else {
            throw error(
                    file,
                    id,
                    path + ".expect.outcome",
                    "unknown outcome '" + expect.outcome() + "' (expected 'success' or 'failure')");
        }
        if (success) {
            if (expect.exception() != null || expect.code() != null || expect.messageContains() != null
                    || expect.slotErrors() != null) {
                throw error(
                        file,
                        id,
                        path + ".expect",
                        "a success expectation must not carry failure-only fields (exception, code, messageContains,"
                                + " slotErrors)");
            }
        } else {
            if (expect.exception() == null && expect.code() == null) {
                throw error(
                        file,
                        id,
                        path + ".expect",
                        "a failure expectation must name the expected exception or the expected error code");
            }
            if (expect.promptTextEqualsGolden() != null || expect.metadata() != null || expect.params() != null
                    || expect.differential() != null) {
                throw error(
                        file,
                        id,
                        path + ".expect",
                        "a failure expectation must not carry success-only fields (promptTextEqualsGolden, metadata,"
                                + " params, differential)");
            }
        }
        List<Expectation.SlotError> slotErrors = new ArrayList<>();
        if (expect.slotErrors() != null) {
            for (int i = 0; i < expect.slotErrors().size(); i++) {
                RawSlotError slotError = expect.slotErrors().get(i);
                if (slotError.slot() == null || slotError.slot().isBlank()) {
                    throw error(file, id, path + ".expect.slotErrors[" + i + "].slot", "the slot name must not be blank");
                }
                if (slotError.code() == null || slotError.code().isBlank()) {
                    throw error(file, id, path + ".expect.slotErrors[" + i + "].code", "the slot error code must not be blank");
                }
                slotErrors.add(new Expectation.SlotError(slotError.slot(), slotError.code()));
            }
        }
        Map<String, Object> params = Map.of();
        if (expect.params() != null && !expect.params().isNull()) {
            if (!expect.params().isObject()) {
                throw error(file, id, path + ".expect.params", "the expected params must be a JSON object");
            }
            params = MAPPER.convertValue(expect.params(), new TypeReference<Map<String, Object>>() {});
        }
        List<String> messageContains =
                expect.messageContains() == null ? List.of() : List.copyOf(expect.messageContains());
        if (!messageContains.isEmpty() && messageContains.stream().anyMatch(String::isBlank)) {
            throw error(file, id, path + ".expect.messageContains", "messageContains entries must not be blank");
        }
        List<String> contracts = expect.contracts() == null ? List.of() : List.copyOf(expect.contracts());
        if (!contracts.isEmpty() && contracts.stream().anyMatch(String::isBlank)) {
            throw error(file, id, path + ".expect.contracts", "contract names must not be blank");
        }
        Expectation.Metadata metadata = expect.metadata() == null
                ? null
                : new Expectation.Metadata(expect.metadata().templateUriEcho(), expect.metadata().contextEcho());
        return new Expectation(
                success,
                expect.exception(),
                expect.code(),
                messageContains,
                slotErrors,
                expect.llmCalls(),
                expect.promptTextEqualsGolden(),
                metadata,
                params,
                contracts,
                Boolean.TRUE.equals(expect.differential()));
    }

    private static ScenarioCase.@Nullable ExpectFlow buildExpectFlow(
            @Nullable RawExpectFlow expectFlow, String file, String id, String path) {
        if (expectFlow == null) {
            return null;
        }
        if (expectFlow.terminalCondition() != null
                && !SUPPORTED_TERMINAL_CONDITIONS.contains(expectFlow.terminalCondition())) {
            throw error(
                    file,
                    id,
                    path + ".expectFlow.terminalCondition",
                    "unknown terminal condition '" + expectFlow.terminalCondition()
                            + "' (known conditions: accept, reject, abort, exhausted)");
        }
        if (expectFlow.roundsUsed() != null && expectFlow.roundsUsed() < 1) {
            throw error(file, id, path + ".expectFlow.roundsUsed", "roundsUsed must be at least 1");
        }
        return new ScenarioCase.ExpectFlow(
                expectFlow.terminalCondition(), expectFlow.roundsUsed(), expectFlow.distinctMessages());
    }

    // ------------------------------------------------------------------ $ref and $fail resolution

    private static List<LlmScriptStep> resolveScript(
            List<JsonNode> script, String file, String id, String path, SharedRefs shared) {
        List<LlmScriptStep> steps = new ArrayList<>(script.size());
        for (int i = 0; i < script.size(); i++) {
            JsonNode stepNode = script.get(i);
            String stepPath = path + "[" + i + "]";
            if (stepNode.isTextual()) {
                steps.add(new LlmScriptStep.Payload(stepNode.asText()));
            } else if (stepNode.isObject()) {
                if (stepNode.size() == 1 && stepNode.has("$fail")) {
                    steps.add(new LlmScriptStep.Fail(parseFailMarker(stepNode.get("$fail"), file, id, stepPath)));
                } else if (stepNode.size() == 1 && stepNode.has("$ref")) {
                    JsonNode payload = resolveSharedRef(
                            stepNode.get("$ref"),
                            file,
                            id,
                            stepPath + ".$ref",
                            true,
                            shared,
                            new LinkedHashSet<>());
                    if (!payload.isTextual()) {
                        throw error(file, id, stepPath + ".$ref", "the referenced response payload must be a JSON string");
                    }
                    steps.add(new LlmScriptStep.Payload(payload.asText()));
                } else {
                    throw error(
                            file,
                            id,
                            stepPath,
                            "a script step must be a literal JSON string, a {\"$ref\": ...} object or a {\"$fail\": ...}"
                                    + " object");
                }
            } else {
                throw error(
                        file,
                        id,
                        stepPath,
                        "a script step must be a literal JSON string, a {\"$ref\": ...} object or a {\"$fail\": ...}"
                                + " object");
            }
        }
        return steps;
    }

    private static LlmFailMarker parseFailMarker(JsonNode node, String file, String id, String path) {
        if (!node.isTextual()) {
            throw error(file, id, path, "the $fail marker must be a string");
        }
        LlmFailMarker marker = LlmFailMarker.fromJsonName(node.asText());
        if (marker == null) {
            throw error(
                    file,
                    id,
                    path,
                    "unknown $fail marker '" + node.asText() + "' (known markers: " + knownFailMarkerNames() + ")");
        }
        return marker;
    }

    private static @Nullable JsonNode resolveSchema(
            @Nullable JsonNode schema, String file, String id, String path, SharedRefs shared) {
        if (schema == null || schema.isNull()) {
            return null;
        }
        if (!schema.isObject()) {
            throw error(file, id, path + ".schema", "the schema must be a JSON object or a $ref object");
        }
        if (schema.size() == 1 && schema.has("$ref")) {
            JsonNode refNode = schema.get("$ref");
            if (!refNode.isTextual()) {
                throw error(file, id, path + ".schema.$ref", "the $ref must be a string");
            }
            JsonNode resolved = resolveSharedRef(
                    refNode, file, id, path + ".schema.$ref", false, shared, new LinkedHashSet<>());
            if (!resolved.isObject()) {
                throw error(file, id, path + ".schema.$ref", "the referenced schema must be a JSON object");
            }
            rejectNestedRefs(resolved, file, id, path + ".schema");
            return resolved;
        }
        rejectNestedRefs(schema, file, id, path + ".schema");
        return schema;
    }

    private static JsonNode resolveSharedRef(
            JsonNode refNode,
            String file,
            String id,
            String path,
            boolean responseScope,
            SharedRefs shared,
            Set<String> visited) {
        if (!refNode.isTextual()) {
            throw error(file, id, path, "the $ref must be a string");
        }
        String ref = refNode.asText();
        String prefix = responseScope ? RESPONSES_PREFIX : SCHEMAS_PREFIX;
        String otherPrefix = responseScope ? SCHEMAS_PREFIX : RESPONSES_PREFIX;
        String sharedFile = responseScope ? RESPONSES_FILE : SCHEMAS_FILE;
        if (ref.startsWith(otherPrefix)) {
            throw error(
                    file,
                    id,
                    path,
                    "$ref '" + ref + "' is out of scope: it must address " + sharedFile + " through the " + prefix
                            + " prefix");
        }
        if (!ref.startsWith(prefix)) {
            throw error(
                    file,
                    id,
                    path,
                    "$ref '" + ref + "' is out of scope: it must address shared/llm-responses.json through the"
                            + " responses/ prefix or shared/schemas.json through the schemas/ prefix");
        }
        if (!visited.add(ref)) {
            throw error(file, id, path, "circular $ref chain detected at '" + ref + "'");
        }
        String key = ref.substring(prefix.length());
        if (key.isEmpty()) {
            throw error(file, id, path, "$ref '" + ref + "' carries no name after the " + prefix + " prefix");
        }
        JsonNode value = (responseScope ? shared.responses() : shared.schemas()).get(key);
        if (value == null) {
            throw error(
                    file,
                    id,
                    path,
                    "dangling $ref '" + ref + "': '" + key + "' is not defined in " + sharedFile);
        }
        if (value.isObject() && value.size() == 1 && value.has("$ref")) {
            String innerRef = value.get("$ref").asText();
            if (visited.contains(innerRef)) {
                throw error(
                        file,
                        id,
                        path,
                        "circular $ref chain '" + ref + "' -> '" + innerRef + "'");
            }
            throw error(
                    file,
                    id,
                    path,
                    "nested $ref '" + innerRef + "' inside '" + ref
                            + "' is not allowed: shared values are literal and resolve one level only");
        }
        return value;
    }

    private static void rejectNestedRefs(JsonNode node, String file, String id, String path) {
        if (node.isObject()) {
            if (node.size() == 1 && node.has("$ref")) {
                throw error(
                        file,
                        id,
                        path,
                        "nested $ref '" + node.get("$ref").asText()
                                + "' is not allowed: references resolve one level only");
            }
            node.fields().forEachRemaining(
                    field -> rejectNestedRefs(field.getValue(), file, id, path + "." + field.getKey()));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                rejectNestedRefs(node.get(i), file, id, path + "[" + i + "]");
            }
        }
    }

    // ------------------------------------------------------------------ id bookkeeping

    private static void claimBaseId(String id, String file, String path, Map<String, String> seenBaseIds) {
        String seenIn = seenBaseIds.putIfAbsent(id, file);
        if (seenIn != null) {
            throw error(
                    file,
                    id,
                    path,
                    "duplicate id '" + id + "': the id is the primary key of the corpus and is also defined in "
                            + seenIn);
        }
    }

    private static void claimExpandedId(
            String expandedId, String file, String id, String path, Map<String, String> seenExpandedIds) {
        String seenIn = seenExpandedIds.putIfAbsent(expandedId, file);
        if (seenIn != null) {
            throw error(
                    file,
                    id,
                    path,
                    "duplicate expanded id '" + expandedId + "': the record is also expanded in " + seenIn);
        }
    }

    // ------------------------------------------------------------------ strict binding

    private static <T> T bind(JsonNode node, Class<T> type, String file, String id, String path) {
        try {
            return MAPPER.treeToValue(node, type);
        } catch (UnrecognizedPropertyException exception) {
            throw error(
                    file,
                    id,
                    path + renderJacksonPath(exception),
                    "unknown property '" + exception.getPropertyName() + "'");
        } catch (JsonProcessingException exception) {
            throw error(file, id, path, "malformed record: " + exception.getOriginalMessage());
        }
    }

    private static String renderJacksonPath(JsonMappingException exception) {
        StringBuilder rendered = new StringBuilder();
        for (JsonMappingException.Reference reference : exception.getPath()) {
            String fieldName = reference.getFieldName();
            if (fieldName != null) {
                rendered.append('.').append(fieldName);
            } else if (reference.getIndex() >= 0) {
                rendered.append('[').append(reference.getIndex()).append(']');
            }
        }
        return rendered.toString();
    }

    private static CorpusLoadException error(String file, @Nullable String id, String path, String message) {
        return new CorpusLoadException(
                file + (id == null ? "" : " [" + id + "]") + " " + path + ": " + message);
    }

    private static String knownApiNames() {
        List<String> names = new ArrayList<>();
        for (NegotiationApi api : NegotiationApi.values()) {
            names.add(api.jsonName());
        }
        return String.join(", ", names);
    }

    private static String knownFailMarkerNames() {
        List<String> names = new ArrayList<>();
        for (LlmFailMarker marker : LlmFailMarker.values()) {
            names.add(marker.jsonName());
        }
        return String.join(", ", names);
    }

    private static Map<String, String> toStringMap(JsonNode object) {
        Map<String, String> values = new LinkedHashMap<>();
        object.fields().forEachRemaining(field -> values.put(field.getKey(), field.getValue().asText()));
        return values;
    }

    private static Map<String, JsonNode> toNodeMap(JsonNode object) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        object.fields().forEachRemaining(field -> values.put(field.getKey(), field.getValue()));
        return values;
    }

    // ------------------------------------------------------------------ raw JSON records

    private record RawCase(
            String id,
            @Nullable String api,
            @Nullable List<String> languages,
            @Nullable String priority,
            @Nullable List<String> tags,
            @Nullable String summary,
            @Nullable RawContext context,
            @Nullable String templateUri,
            @Nullable RawInput input,
            @Nullable RawLlm llm,
            @Nullable RawPrompt prompt,
            @Nullable JsonNode schema,
            @Nullable String inject,
            @Nullable RawExpect expect) {}

    private record RawScenario(
            String id,
            @Nullable String summary,
            @Nullable List<String> languages,
            @Nullable List<String> roles,
            @Nullable List<RawStep> steps,
            @Nullable RawExpectFlow expectFlow) {}

    private record RawStep(
            Integer step,
            @Nullable String role,
            @Nullable String api,
            @Nullable RawContext context,
            @Nullable String templateUri,
            @Nullable RawInput input,
            @Nullable RawLlm llm,
            @Nullable RawPrompt prompt,
            @Nullable JsonNode schema,
            @Nullable String inject,
            @Nullable RawExpect expect) {}

    private record RawContext(String id, Integer round, Integer maxRounds) {}

    private record RawInput(@Nullable Map<String, String> text, @Nullable JsonNode data) {}

    private record RawLlm(@Nullable Integer maxAttempts, @Nullable List<JsonNode> script) {}

    private record RawPrompt(@Nullable String golden, @Nullable String text, @Nullable Integer fromStep) {}

    private record RawExpect(
            @Nullable String outcome,
            @Nullable String exception,
            @Nullable String code,
            @Nullable List<String> messageContains,
            @Nullable List<RawSlotError> slotErrors,
            @Nullable Integer llmCalls,
            @Nullable String promptTextEqualsGolden,
            @Nullable RawMetadata metadata,
            @Nullable JsonNode params,
            @Nullable List<String> contracts,
            @Nullable Boolean differential) {}

    private record RawSlotError(String slot, String code) {}

    private record RawMetadata(@Nullable String templateUriEcho, @Nullable Boolean contextEcho) {}

    private record RawExpectFlow(
            @Nullable String terminalCondition, @Nullable Integer roundsUsed, @Nullable Boolean distinctMessages) {}
}
