package net.openan.a2at.sample.task_t;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Field-level and sample-level accuracy scoring for the {@code Task-T} demo.
 *
 * <p>Each {@link SampleScore} scores one sample: every expected ground-truth field is compared with the corresponding
 * value extracted by {@code A2ATServer#validateAndFillingTaskData}. {@link Summary} aggregates the scores per client
 * API both as a field hit rate (matched expected fields over total expected fields) and as a sample pass rate
 * (samples whose expected fields all hit over total samples).
 */
final class TaskTAccuracyEvaluator {

    private TaskTAccuracyEvaluator() {
    }

    /**
     * One expected slot compared against the extracted value.
     *
     * @param slot Chinese slot name
     * @param expected ground-truth value
     * @param extracted value extracted by the server facade; {@code null} when it was missing
     * @param matched whether the extracted value hit the expected value
     * @param detail explanation for a mismatch or a validation failure
     */
    record FieldScore(String slot, String expected, String extracted, boolean matched, String detail) {}

    /**
     * Accuracy outcome of one sample.
     *
     * @param name sample identifier
     * @param validationPassed whether the server validation step completed without a {@code ContentValidationException}
     * @param fields per-field scores; empty when generation or validation failed
     */
    record SampleScore(String name, boolean validationPassed, List<FieldScore> fields) {

        int expectedFieldCount() {
            return fields.size();
        }

        int matchedFieldCount() {
            return (int) fields.stream().filter(FieldScore::matched).count();
        }

        /** A sample passes when validation succeeded and every expected field hit. */
        boolean passed() {
            return validationPassed && matchedFieldCount() == expectedFieldCount();
        }
    }

    /**
     * Aggregated accuracy numbers for one client API case.
     *
     * @param api case label shown in the report
     * @param sampleCount total scored samples
     * @param passedSamples samples whose expected fields all hit
     * @param matchedFields field hits over all scored samples
     * @param expectedFields total expected fields over all scored samples
     */
    record Summary(String api, int sampleCount, int passedSamples, int matchedFields, int expectedFields) {

        double fieldAccuracyPercent() {
            return expectedFields == 0 ? 0d : 100d * matchedFields / expectedFields;
        }

        double samplePassRatePercent() {
            return sampleCount == 0 ? 0d : 100d * passedSamples / sampleCount;
        }
    }

    /**
     * Scores one sample: each expected value is looked up in the extracted parameter map and compared.
     *
     * @param sample source sample
     * @param extractedParams values extracted by the server facade; may carry extra slots beyond the expected ones
     * @return per-field score list in sample insertion order
     */
    static List<FieldScore> scoreFields(TaskTSample sample, Map<String, Object> extractedParams) {
        List<FieldScore> scores = new ArrayList<>();
        sample.expectedParams()
                .forEach((slot, expected) -> {
                    Object extracted = extractedParams.get(slot);
                    String extractedText = extracted == null ? null : String.valueOf(extracted);
                    boolean matched = matches(extractedText, expected);
                    String detail = matched ? "" : "expected=" + expected + ", extracted=" + (extractedText == null
                                    ? "<missing>"
                                    : extractedText);
                    scores.add(new FieldScore(slot, expected, extractedText, matched, detail));
                });
        return scores;
    }

    /**
     * Hit rule: equal after normalization, or one contains the other.
     *
     * @param extracted extracted value, may be {@code null}
     * @param expected ground-truth value
     * @return {@code true} when both are non-blank and the normalized values are equal or contain each other
     */
    static boolean matches(String extracted, String expected) {
        String normalizedExtracted = normalize(extracted);
        String normalizedExpected = normalize(expected);
        if (normalizedExtracted.isEmpty() || normalizedExpected.isEmpty()) {
            return false;
        }
        return normalizedExtracted.equals(normalizedExpected)
                || normalizedExtracted.contains(normalizedExpected)
                || normalizedExpected.contains(normalizedExtracted);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Aggregates scored samples into one per-API summary.
     *
     * @param api case label shown in the report
     * @param scores scored samples
     * @return aggregated summary
     */
    static Summary summarize(String api, List<SampleScore> scores) {
        int passedSamples = (int) scores.stream().filter(SampleScore::passed).count();
        int matchedFields = scores.stream().mapToInt(SampleScore::matchedFieldCount).sum();
        int expectedFields = scores.stream().mapToInt(SampleScore::expectedFieldCount).sum();
        return new Summary(api, scores.size(), passedSamples, matchedFields, expectedFields);
    }
}