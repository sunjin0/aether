package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 运行记录
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_run")
@ApiModel(value = "AgentRun对象", description = "运行记录")
public class AgentRun extends BaseEntity {

    /** 统一 Execution 账本中的节点 ID。 */
    private String executionId;

    @ApiModelProperty(value = "所属业务应用空间")
    private String applicationId;

    @ApiModelProperty(value = "业务系统标识")
    private String businessId;

    @ApiModelProperty(value = "关联Agent定义ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "用户ID")
    private String userId;

    @ApiModelProperty(value = "关联会话ID")
    private String conversationId;

    @ApiModelProperty(value = "持续 Agent 会话 ID")
    private String sessionId;

    @ApiModelProperty(value = "持续 Agent 任务 ID")
    private String taskId;

    /**
     * 同一 Task 的第几次执行尝试；恢复/补充输入创建新 Run 时递增。
     */
    @ApiModelProperty(value = "Task execution attempt number")
    private Integer attemptNo;

    @ApiModelProperty(value = "关联输出消息ID")
    private String messageId;

    @ApiModelProperty(value = "输入内容摘要")
    private String inputContent;

    @ApiModelProperty(value = "输出内容摘要")
    private String outputContent;

    @ApiModelProperty(value = "模型供应商原始响应")
    private String rawResponse;

    @ApiModelProperty(value = "使用的模型")
    private String model;

    @ApiModelProperty(value = "使用的模型供应商ID")
    private String modelProviderId;

    @ApiModelProperty(value = "输入token数")
    private Integer promptTokens;

    @ApiModelProperty(value = "输出token数")
    private Integer completionTokens;

    @ApiModelProperty(value = "总token数")
    private Integer totalTokens;

    @ApiModelProperty(value = "总耗时（毫秒）")
    private Integer latencyMs;

    @ApiModelProperty(value = "等待用户输入/审批的耗时（毫秒）；执行耗时 = latencyMs - waitingMs")
    private Long waitingMs;

    @ApiModelProperty(value = "状态：0-成功，1-失败，2-超时，3-排队/等待用户，4-运行中，5-客户端取消")
    private Integer status;

    @ApiModelProperty(value = "错误信息")
    private String errorMsg;

    @ApiModelProperty(value = "External Deep Agent run ID")
    private String externalRunId;

    /** 调用方传入的稳定幂等键，用于普通 Agent 请求去重。 */
    @ApiModelProperty(value = "请求幂等ID")
    private String requestId;

    @ApiModelProperty(value = "Deep Agent dispatch-time retrieval sources JSON")
    private String retrievalSources;

    @ApiModelProperty(value = "Execution mode: STANDARD or DEEP")
    private String executionMode;

    /**
     * 本次运行冻结的 Skill、工具和知识库作用域快照 JSON。
     */
    @ApiModelProperty(value = "Resolved Skill context snapshot JSON")
    private String skillSnapshot;

    private String productProfileId;
    private String productSnapshotId;
    private String serviceAccountId;
    private String trustedContext;
    private Integer contextVersion;
    /** SHA-256 of the canonical OpenAPI request. */
    private String requestFingerprint;
}
