package com.aether.agent.skill.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能版本冻结的资源元数据，不保存或执行脚本正文。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_skill_resource")
public class AgentSkillResource extends BaseEntity {
    private String skillVersionId;
    private String name;
    private String type;
    private String language;
    private String objectKey;
    private String contentSha256;
    private Long size;
    private String purpose;
    private Integer status;
}
