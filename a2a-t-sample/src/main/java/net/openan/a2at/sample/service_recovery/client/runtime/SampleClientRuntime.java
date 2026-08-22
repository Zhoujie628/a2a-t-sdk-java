package net.openan.a2at.sample.service_recovery.client.runtime;

import net.openan.a2at.sample.service_recovery.client.prompt.SamplePromptClient;
import net.openan.a2at.sample.service_recovery.client.registry.SampleRegistryClient;

/**
 * Runtime bundle required by the client sample main flow.
 *
 * @since 2026-08
 */
public interface SampleClientRuntime extends AutoCloseable {

    /**
     * Returns the registry client used to resolve the server AgentCard.
     *
     * @return registry client
     */
    SampleRegistryClient registryClient();

    /**
     * Returns the prompt-generation bridge over the SDK client facade.
     *
     * @return prompt client
     */
    SamplePromptClient promptClient();

    @Override
    void close();
}
