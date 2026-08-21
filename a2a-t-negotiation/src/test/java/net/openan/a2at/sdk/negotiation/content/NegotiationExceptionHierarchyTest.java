package net.openan.a2at.sdk.negotiation.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.A2ATParamExtractionError;
import org.junit.jupiter.api.Test;

class NegotiationExceptionHierarchyTest {

    private static final Class<?>[] NEGOTIATION_EXCEPTION_TYPES = {
        NegotiationProcessingException.class,
        NegotiationGenerationException.class,
        NegotiationParamExtractionException.class
    };

    @Test
    void processingExceptionIsAProcessingErrorWithCode() {
        NegotiationProcessingException exception =
                new NegotiationProcessingException(A2ATErrorCodes.NEGOTIATION_INVALID_INPUT, "invalid input");

        assertTrue(exception instanceof A2ATError);
        assertEquals(A2ATErrorCodes.NEGOTIATION_INVALID_INPUT, exception.getCode());
    }

    @Test
    void processingExceptionInheritsGetCodeFromTheA2ATErrorRoot() {
        NegotiationProcessingException exception =
                new NegotiationProcessingException(A2ATErrorCodes.NEGOTIATION_INVALID_INPUT, "invalid input");

        assertEquals(
                A2ATError.class,
                findGetMethod(NegotiationProcessingException.class).getDeclaringClass(),
                "the code accessor must come from the A2ATError root so every negotiation failure shares one code"
                        + " contract");
        assertEquals(A2ATErrorCodes.NEGOTIATION_INVALID_INPUT, exception.getCode());
    }

    @Test
    void generationExceptionExtendsProcessingException() {
        NegotiationGenerationException exception =
                new NegotiationGenerationException(A2ATErrorCodes.NEGOTIATION_SLOT_MISSING, "missing slot");

        assertTrue(exception instanceof NegotiationProcessingException);
        assertTrue(exception instanceof A2ATError);
        assertEquals(A2ATErrorCodes.NEGOTIATION_SLOT_MISSING, exception.getCode());
    }

    @Test
    void paramExtractionExceptionExtendsSharedParamExtractionError() {
        NegotiationParamExtractionException defaultCodedException =
                new NegotiationParamExtractionException("extraction failed");
        NegotiationParamExtractionException codedException = new NegotiationParamExtractionException(
                A2ATErrorCodes.NEGOTIATION_RULE_VIOLATION, "rule violated", List.of());

        assertTrue(defaultCodedException instanceof A2ATParamExtractionError);
        assertTrue(codedException instanceof A2ATParamExtractionError);
        assertEquals(A2ATErrorCodes.PARAM_EXTRACTION_FAILED, defaultCodedException.getCode());
        assertEquals(A2ATErrorCodes.NEGOTIATION_RULE_VIOLATION, codedException.getCode());
        assertTrue(codedException.getErrors().isEmpty());
    }

    @Test
    void noNegotiationExceptionExposesAStageProperty() {
        for (Class<?> exceptionType : NEGOTIATION_EXCEPTION_TYPES) {
            boolean exposesStage = Arrays.stream(exceptionType.getMethods())
                    .anyMatch(method -> "getStage".equals(method.getName()) || "stage".equals(method.getName()));
            assertFalse(exposesStage, exceptionType.getSimpleName() + " must not expose a stage property");
        }
    }

    private static java.lang.reflect.Method findGetMethod(Class<?> exceptionType) {
        try {
            return exceptionType.getMethod("getCode");
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("getCode must be inherited from A2ATError", exception);
        }
    }
}
