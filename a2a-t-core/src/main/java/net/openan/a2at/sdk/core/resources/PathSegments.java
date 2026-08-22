package net.openan.a2at.sdk.core.resources;

import org.jspecify.annotations.Nullable;

/**
 * Validates path segment values used to compose classpath or filesystem resource paths.
 *
 * <p>A simple segment is non-null, non-blank and free of slashes, backslashes and {@code ..} sequences, so that it
 * can never escape the resource root it is resolved against.
 *
 * @since 2026-08
 */
public final class PathSegments {

    private PathSegments() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Checks whether the value is a non-blank simple path segment.
     *
     * @param value candidate path segment such as a language or category identifier
     * @return true when the value is non-null, non-blank and contains no slash, backslash or {@code ..} sequence
     */
    public static boolean isSimpleSegment(@Nullable String value) {
        return value != null
                && !value.isBlank()
                && !value.contains("/")
                && !value.contains("\\")
                && !value.contains("..");
    }
}
