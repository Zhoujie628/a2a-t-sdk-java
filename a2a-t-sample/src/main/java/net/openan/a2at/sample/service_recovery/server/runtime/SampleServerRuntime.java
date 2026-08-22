package net.openan.a2at.sample.service_recovery.server.runtime;

import java.nio.file.Path;
import java.util.Map;
import net.openan.a2at.sample.service_recovery.server.flow.NotificationPromptValidator;

/**
 * Runtime assembly abstraction for the server sample bootstrap flow.
 *
 * @since 2026-08
 */
public interface SampleServerRuntime {

    /**
     * Resolves the HTTP bind address for this run.
     *
     * @return resolved bind pair
     */
    ServerBind resolveBind();

    /**
     * Builds the sample AgentCard payload for the bind address.
     *
     * @param host bind host
     * @param port bind port
     * @return AgentCard payload map
     */
    Map<String, Object> buildAgentCard(String host, int port);

    /**
     * Builds the notification prompt validator backed by the server SDK.
     *
     * @param envPath resolved environment file path
     * @return validator wrapping {@code A2ATServer.validateAndFillingNotificationData}
     */
    NotificationPromptValidator buildNotificationValidator(Path envPath);

    /**
     * Builds the a2a-java request handler application.
     *
     * @param agentCard sample AgentCard payload
     * @param notificationValidator notification prompt validator used by the agent executor
     * @return assembled request handler
     */
    Object buildApp(Map<String, Object> agentCard, NotificationPromptValidator notificationValidator);

    /**
     * Registers the AgentCard with the registry center.
     *
     * @param registrationPayload registry registration payload
     * @param envPath resolved environment file path
     * @return registration result payload
     */
    Map<String, Object> registerAgentCard(Map<String, Object> registrationPayload, Path envPath);
}
