package com.aether.workflow.runtime;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.workflow.service.AgentWorkflowVersionService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按实例绑定的已发布版本输出契约筛选业务可见结果。
 */
@Component
public class WorkflowOutputResolver {
    private final AgentWorkflowVersionService versionService;

    public WorkflowOutputResolver(AgentWorkflowVersionService versionService) {
        this.versionService = versionService;
    }

    /**
     * 只返回 outputSchema 显式声明且已有值的字段，绝不暴露流程内部变量。
     */
    public Map<String, Object> resolve(AgentWorkflowInstance instance) {
        return resolve(instance, null);
    }

    /** 使用 Capability 的更窄输出契约进一步限制发布版本输出。 */
    public Map<String, Object> resolve(AgentWorkflowInstance instance, String capabilitySchema) {
        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        if (instance == null || StringUtils.isBlank(instance.getVariables())) return outputs;
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        if (version == null || StringUtils.isBlank(version.getOutputSchema())) return outputs;
        try {
            JSONObject variables = JSONObject.parseObject(instance.getVariables());
            java.util.Set<String> names = schemaNames(version.getOutputSchema());
            if (StringUtils.isNotBlank(capabilitySchema)) names.retainAll(schemaNames(capabilitySchema));
            for (String name : names) if (variables.containsKey(name)) outputs.put(name, variables.get(name));
        } catch (Exception ignored) {
            // 实例历史数据或定义损坏时，安全地返回空输出而非内部上下文。
        }
        return outputs;
    }

    private java.util.Set<String> schemaNames(String schemaText) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        if (StringUtils.isBlank(schemaText)) return names;
        if (schemaText.trim().startsWith("{")) {
            JSONObject schema = JSONObject.parseObject(schemaText);
            JSONObject properties = schema == null ? null : schema.getJSONObject("properties");
            if (properties != null) names.addAll(properties.keySet());
        } else for (Object item : JSONArray.parseArray(schemaText)) {
            if (item instanceof JSONObject) {
                String name = ((JSONObject) item).getString("name");
                if (StringUtils.isNotBlank(name)) names.add(name);
            }
        }
        return names;
    }
}
