package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_review_action_log")
public class KnowledgeReviewActionLog extends BaseEntity {
    /** 审核任务 ID。 */
    private String reviewTaskId;
    /** 关联的知识文档 ID。 */
    private String documentId;
    /** 关联的知识文档版本 ID。 */
    private String documentVersionId;
    /** 执行操作的管理员 ID。 */
    private String operatorId;
    /** 操作类型：SUBMITTED-提交审核，CLAIMED-认领审核，APPROVED-审核通过，REJECTED-审核驳回。 */
    private String action;
    /** 操作前状态：文档状态 DRAFT/AI_REVIEWING/AI_REVIEWED/SUBMITTED/APPROVED/REJECTED；认领时为任务状态 pending。 */
    private String beforeStatus;
    /** 操作后状态：文档状态 DRAFT/AI_REVIEWING/AI_REVIEWED/SUBMITTED/APPROVED/REJECTED；认领时为任务状态 claimed。 */
    private String afterStatus;
    /** 操作备注；提交时为提交说明，驳回时为驳回原因，审核通过时为审核意见。 */
    private String comment;
    /** 扩展元数据，JSON 格式。 */
    private String metadata;
}
