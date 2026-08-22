package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.validation.TemplateUri;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.validation.NegotiationSemanticValidator;
import org.junit.jupiter.api.Test;

/**
 * Pins the compile-time contract of the validate-and-filling API: the parameter schema and the template URI are
 * mandatory parts of the full signature of each of the three methods, so a caller that omits either argument is
 * rejected at compile time and no reduced-arity overload can silently appear later.
 */
class ValidateAndFillingSignatureContractTest {

    private static final List<String> VALIDATE_AND_FILLING_METHODS =
            List.of("validateAndFillingProposeData", "validateAndFillingAcceptData", "validateAndFillingRejectData");

    @Test
    void everyValidateAndFillingMethodHasExactlyTheFullThreeArgumentSignature() {
        for (String methodName : VALIDATE_AND_FILLING_METHODS) {
            List<Method> declared = Arrays.stream(NegotiationGenerationOrchestrator.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals(methodName))
                    .toList();

            assertEquals(
                    1,
                    declared.size(),
                    methodName + " must exist exactly once: no reduced-arity overload may bypass the mandatory"
                            + " schema and template URI arguments");
            Method method = declared.get(0);
            assertTrue(Modifier.isPublic(method.getModifiers()), methodName + " must be public");
            Class<?>[] parameterTypes = method.getParameterTypes();
            assertEquals(3, parameterTypes.length, methodName + " must take exactly three parameters");
            assertEquals(String.class, parameterTypes[0], methodName + " first parameter is the prompt text");
            assertEquals(Map.class, parameterTypes[1], methodName + " second parameter is the caller parameter schema");
            assertEquals(TemplateUri.class, parameterTypes[2], methodName + " third parameter is the template URI");
        }
    }

    @Test
    void semanticValidatorSeamCarriesThePromptSchemaAndReferenceTogether() throws NoSuchMethodException {
        Method validate = NegotiationSemanticValidator.class.getMethod(
                "validate", String.class, Map.class, NegotiationReference.class);

        assertTrue(Modifier.isPublic(validate.getModifiers()));
        assertEquals(
                3,
                validate.getParameterCount(),
                "the semantic validator seam must require the prompt, the caller schema and the template reference"
                        + " together");
    }
}
