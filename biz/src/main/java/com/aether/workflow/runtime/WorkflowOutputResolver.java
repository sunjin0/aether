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
        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        if (instance == null || StringUtils.isBlank(instance.getVariables())) return outputs;
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        if (version == null || StringUtils.isBlank(version.getOutputSchema())) return outputs;
        try {
            JSONObject variables = JSONObject.parseObject(instance.getVariables());
            for (Object item : JSONArray.parseArray(version.getOutputSchema())) {
                if (!(item instanceof JSONObject)) continue;
                String name = ((JSONObject) item).getString("name");
                if (StringUtils.isNotBlank(name) && variables.containsKey(name)) outputs.put(name, variables.get(name));
            }
        } catch (Exception ignored) {
            // 实例历史数据或定义损坏时，安全地返回空输出而非内部上下文。
        }
        return outputs;
    }
}
