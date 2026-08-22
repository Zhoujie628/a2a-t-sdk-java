package net.openan.a2at.sample.service_recovery.client.runtime;

import java.util.Map;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.MessageSendParams;

/**
 * Transitional seam for assembling the real a2a-java client runtime.
 *
 * @since 2026-08
 */
public interface A2AJavaClientRuntime {

    /**
     * Sends one message:stream request through the real a2a-java client.
     *
     * @param agentCard resolved registry AgentCard payload
     * @param request message send parameters
     * @param callContext call context carrying the A2A-Extensions header
     * @param logSink log sink, may be null
     * @return iterable of received client events
     */
    Iterable<ClientEvent> sendMessage(
            Map<String, Object> agentCard,
            MessageSendParams request,
            ClientCallContext callContext,
            Consumer<String> logSink);
}
