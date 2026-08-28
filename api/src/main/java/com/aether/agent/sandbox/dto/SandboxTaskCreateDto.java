package com.aether.agent.sandbox.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * User-visible request; execution boundaries are always sourced from a template.
 */
@Data
@ApiModel("沙箱任务创建请求")
public class SandboxTaskCreateDto {
    @ApiModelProperty(value = "已发布沙箱模板编码", required = true, example = "python-data-analysis")
    private String templateCode;
    @ApiModelProperty(value = "来源智能体定义 ID", example = "agent-123")
    private String agentDefinitionId;
    @ApiModelProperty(value = "来源智能体运行 ID", example = "run-123")
    private String runId;
    @ApiModelProperty(value = "来源智能体消息 ID", example = "message-123")
    private String messageId;
    /**
     * Accepted only when the published template explicitly declares a script slot.
     */
    @ApiModelProperty(value = "脚本源码；仅当模板声明脚本槽位时接受", example = "print('hello, sandbox')")
    private String script;
    @ApiModelProperty(value = "脚本语言", example = "python")
    private String scriptLanguage;
    /**
     * IDs of requester-owned artifact-library files; callers never provide object keys or filesystem paths.
     */
    @ApiModelProperty(value = "请求方拥有的输入制品 ID", example = "[\"artifact-123\"]")
    private List<String> inputArtifactIds;
    @ApiModelProperty(value = "供模板使用的结构化输入", example = "{\"reportMonth\":\"2026-01\"}")
    private Map<String, Object> input;
}
