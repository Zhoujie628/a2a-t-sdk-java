package net.openan.a2at.sample.subscribe_incident.server.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import org.junit.jupiter.api.Test;

class DefaultSampleServerRuntimeTest {

    @Test
    void resolveBindUsesDefaultsWhenHostAndPortAreBlank() {
        Path envPath = createEnvFile("A2AT_SAMPLE_HOST=\nA2AT_SAMPLE_PORT=\n");

        ServerBind bind = new DefaultSampleServerRuntime(envPath, message -> {}).resolveBind();

        assertEquals("127.0.0.1", bind.host());
        assertEquals(8000, bind.port());
    }

    @Test
    void createSampleThreadFactoryRegistersUncaughtExceptionHandler() {
        List<String> logs = new ArrayList<>();
        AssertionError failure = new AssertionError("boom");

        ThreadFactory threadFactory = DefaultSampleServerRuntime.createSampleThreadFactory(logs::add);
        Thread thread = threadFactory.newThread(() -> {
            throw failure;
        });
        thread.start();
        assertDoesNotThrow(() -> thread.join(1000L));

        assertEquals("a2a-t-sample-server-1", thread.getName());
        assertTrue(thread.isDaemon());
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("a2a-t-sample-server"));
        assertTrue(logs.get(0).contains(AssertionError.class.getName()));
        assertTrue(logs.get(0).contains("boom"));
    }

    private static Path createEnvFile(String content) {
        try {
            Path envPath = Files.createTempFile("a2a-t-server", ".env");
            Files.writeString(envPath, content);
            return envPath;
        } catch (IOException exception) {
            throw new AssertionError("Failed to create server env test file", exception);
        }
    }
}
