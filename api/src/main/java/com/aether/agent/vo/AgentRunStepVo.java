package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentRunStepVo extends BaseEntity {

    @ApiModelProperty(value = "运行记录 ID")
    private String runId;

    @ApiModelProperty(value = "外部事件 ID，幂等键")
    private String eventId;

    @ApiModelProperty(value = "事件类型：run.started / plan.updated / step.started / tool.started / tool.completed 等")
    private String eventType;

    @ApiModelProperty(value = "事件数据 JSON")
    private String data;

    @ApiModelProperty(value = "事件发生时间戳（毫秒）")
    private Long occurredAt;
}
