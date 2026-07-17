package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_base_member")
public class KnowledgeBaseMember extends BaseEntity {
    /** 被授权的知识库 ID。 */
    private String knowledgeBaseId;
    /** 被授权后台管理员 ID。 */
    private String adminId;
    /** 成员角色：owner-所有者，manager-管理员，editor-可编辑，viewer-只读。 */
    private String role;
}
