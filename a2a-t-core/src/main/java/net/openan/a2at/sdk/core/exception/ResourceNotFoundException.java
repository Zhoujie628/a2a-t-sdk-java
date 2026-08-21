package net.openan.a2at.sdk.core.exception;

import org.jspecify.annotations.NonNull;

/**
 * Raised when a referenced SDK resource cannot be resolved.
 *
 * @since 2026-06
 */
public final class ResourceNotFoundException extends A2ATError {

    private final String resourcePath;

    /**
     * Creates a resource-not-found exception for one SDK classpath resource.
     *
     * @param message failure message
     * @param resourcePath missing classpath resource path
     */
    public ResourceNotFoundException(String message, @NonNull String resourcePath) {
        super(message);
        this.resourcePath = resourcePath;
    }

    /**
     * Returns the missing classpath resource path.
     *
     * @return missing resource path
     */
    public @NonNull String resourcePath() {
        return resourcePath;
    }
}
