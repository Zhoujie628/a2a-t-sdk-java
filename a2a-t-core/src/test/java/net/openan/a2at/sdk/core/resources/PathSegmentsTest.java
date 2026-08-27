package net.openan.a2at.sdk.core.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PathSegmentsTest {

    @Test
    void should_rejectNull_When_valueIsNull() {
        assertFalse(PathSegments.isSimpleSegment(null));
    }

    @Test
    void should_rejectBlank_When_valueIsBlank() {
        assertFalse(PathSegments.isSimpleSegment(""));
        assertFalse(PathSegments.isSimpleSegment("   "));
    }

    @Test
    void should_rejectForwardSlash_When_valueContainsSlash() {
        assertFalse(PathSegments.isSimpleSegment("a/b"));
        assertFalse(PathSegments.isSimpleSegment("/"));
    }

    @Test
    void should_rejectBackslash_When_valueContainsBackslash() {
        assertFalse(PathSegments.isSimpleSegment("a\\b"));
        assertFalse(PathSegments.isSimpleSegment("\\"));
    }

    @Test
    void should_rejectTraversal_When_valueContainsDotDot() {
        assertFalse(PathSegments.isSimpleSegment(".."));
        assertFalse(PathSegments.isSimpleSegment("a/../b"));
    }

    @Test
    void should_acceptValidSegment_When_valueIsSimple() {
        assertTrue(PathSegments.isSimpleSegment("ran-energy-saving"));
        assertTrue(PathSegments.isSimpleSegment("Task-T"));
        assertTrue(PathSegments.isSimpleSegment("zh-CN"));
        assertTrue(PathSegments.isSimpleSegment("a.b"));
    }
}
