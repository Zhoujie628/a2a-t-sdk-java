package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ExtensionUriConstants}.
 *
 * <p>Tests cover the extension URI constants and utility class invariants.
 *
 * @since 2026-08
 */
class ExtensionUriConstantsTest {

    @Test
    void should_haveCorrectTaskTExtensionUri() {
        assertEquals(
                "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/v1",
                ExtensionUriConstants.TASK_T_EXTENSION_URI);
    }

    @Test
    void should_haveCorrectAuthorizationTExtensionUri() {
        assertEquals(
                "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1",
                ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI);
    }

    @Test
    void should_haveCorrectNotificationTExtensionUri() {
        assertEquals(
                "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1",
                ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI);
    }

    @Test
    void should_havePrivateConstructor() throws Exception {
        Constructor<ExtensionUriConstants> constructor =
                ExtensionUriConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(Exception.class, constructor::newInstance);
    }
}