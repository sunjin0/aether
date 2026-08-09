package com.aether.agent.skill.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A published Skill's declarative artifact-execution policy.  It never stores
 * a command, image name, mount path, or network option; those stay platform owned.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_skill_execution_config")
public class AgentSkillExecutionConfig extends BaseEntity {
    private String skillVersionId;
    private Boolean enabled;
    private String entryResourceId;
    private String runtime;
    private String outputFormats;
    private Integer timeoutSeconds;
    private Integer maxOutputFiles;
    private Long maxOutputBytes;
}
