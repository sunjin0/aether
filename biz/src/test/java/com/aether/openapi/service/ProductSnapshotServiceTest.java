package com.aether.openapi.service;

import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.agent.product.entity.AgentProductProfileVersion;
import com.aether.agent.product.service.AgentProductProfileVersionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductSnapshotServiceTest {
    @Test
    void resolvesFrozenAgentFromOwnedSnapshot() {
        AgentProductProfileVersionService versions = mock(AgentProductProfileVersionService.class);
        AgentProductProfile product = new AgentProductProfile(); product.setId("profile-1"); product.setPublishedSnapshotId("snapshot-1");
        AgentProductProfileVersion snapshot = new AgentProductProfileVersion(); snapshot.setProfileId("profile-1"); snapshot.setDeleted(false);
        snapshot.setSnapshot("{\"agent\":{\"id\":\"agent-1\",\"systemPrompt\":\"frozen\"}}");
        when(versions.getById("snapshot-1")).thenReturn(snapshot);

        com.aether.agent.entity.AgentDefinition agent = new ProductSnapshotService(versions).resolveAgent(product);
        assertEquals("agent-1", agent.getId());
        assertEquals("frozen", agent.getSystemPrompt());
    }

    @Test
    void legacyProductFallsBackToLiveAgent() {
        assertNull(new ProductSnapshotService(mock(AgentProductProfileVersionService.class)).resolveAgent(new AgentProductProfile()));
    }
}
