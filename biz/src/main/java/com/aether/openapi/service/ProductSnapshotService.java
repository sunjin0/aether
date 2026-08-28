package com.aether.openapi.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.agent.product.entity.AgentProductProfileVersion;
import com.aether.agent.product.service.AgentProductProfileVersionService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/** Resolves the frozen Agent configuration selected when an OpenAPI product was published. */
@Service
public class ProductSnapshotService {
    private final AgentProductProfileVersionService versionService;

    public ProductSnapshotService(AgentProductProfileVersionService versionService) {
        this.versionService = versionService;
    }

    /** Returns null for legacy products that predate executable publication snapshots. */
    public AgentDefinition resolveAgent(AgentProductProfile product) {
        if (product == null || StringUtils.isBlank(product.getPublishedSnapshotId())) return null;
        AgentProductProfileVersion snapshot = versionService.getById(product.getPublishedSnapshotId());
        if (snapshot == null || Boolean.TRUE.equals(snapshot.getDeleted()) || !StringUtils.equals(snapshot.getProfileId(), product.getId()))
            throw new IllegalStateException("产品发布快照不存在或不匹配");
        JSONObject root = JSON.parseObject(snapshot.getSnapshot());
        JSONObject agent = root == null ? null : root.getJSONObject("agent");
        return agent == null ? null : agent.toJavaObject(AgentDefinition.class);
    }
}
