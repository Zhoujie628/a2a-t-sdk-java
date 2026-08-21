package net.openan.a2at.sdk.resources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.resources.ClasspathResourceStreams;

/**
 * Loads prompt resources from the classpath bundle packaged with the SDK.
 *
 * @since 2026-06
 */
public final class ClasspathPromptResourceLoader {

    /**
     * Loads one UTF-8 text resource from the packaged prompt resource tree.
     *
     * @param key resource key to resolve
     * @return loaded text payload
     */
    public String loadText(PromptResourceKey key) {
        String relativePath = key.relativePath();
        InputStream stream = ClasspathResourceStreams.open(relativePath);
        if (stream == null) {
            throw new ResourceNotFoundException("Prompt resource file does not exist.", relativePath);
        }

        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new A2ATError("Failed to read prompt resource: " + relativePath, error);
        }
    }
}
