package com.aether.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 文档（V0.7预留）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("knowledge_document")
@ApiModel(value = "KnowledgeDocument对象", description = "文档")
public class KnowledgeDocument extends BaseEntity {

    /** 关联知识库 ID。 */
    @ApiModelProperty(value = "关联知识库ID")
    private String knowledgeBaseId;

    /** 文档标题。 */
    @ApiModelProperty(value = "文档标题")
    private String title;

    /** 文档内容，支持纯文本或 Markdown。 */
    @ApiModelProperty(value = "文档内容（纯文本或Markdown）")
    private String content;

    /** 来源 URL；手工录入或文件上传时可为空。 */
    @ApiModelProperty(value = "来源URL（可选）")
    private String sourceUrl;

    /** 文档来源类型：text-文本/Markdown 输入，file-MinIO 上传文件。 */
    private String sourceType;
    /** 上传时的原始文件名；文本输入时可为空。 */
    private String originalFileName;
    /** 文件扩展名，例如 txt、md、pdf、docx。 */
    private String fileExtension;
    /** 浏览器上报的 MIME 类型。 */
    private String mimeType;
    /** 原始文件大小，单位字节。 */
    private Long fileSize;
    /** 原始内容 SHA-256 十六进制摘要，用于去重与版本追踪。 */
    private String fileChecksum;
    /** MinIO Bucket 名称；不向前端暴露访问密钥。 */
    private String storageBucket;
    /** MinIO 对象键，格式为 knowledge/{knowledgeBaseId}/{documentId}/{versionNo}/{fileName}。 */
    private String storageObjectKey;
    /** 已完成或正在索引的当前文档版本号。 */
    private Integer currentVersionNo;
    /** 当前可编辑草稿版本 ID；与已发布版本相互独立。 */
    private String draftVersionId;
    /** 当前已提交、等待审批的版本 ID。 */
    private String submittedVersionId;
    /** 文档工作流聚合状态：DRAFT-草稿，AI_REVIEWING-AI 审查中，AI_REVIEWED-AI 已审查，SUBMITTED-已提交人工审核，APPROVED-已通过，REJECTED-已驳回；版本状态才是事实来源。 */
    private String reviewStatus;
    /** 文档工作流状态最近更新时间，Unix 毫秒时间戳。 */
    private Long reviewUpdatedAt;
    /** 索引状态：0-未索引，1-索引中，2-已完成，3-失败。 */
    private Integer indexStatus;
    /** 解析器类型：text、pdf、docx；文本输入时为 text。 */
    private String parserType;
    /** 最近一次索引失败的错误原因；成功后清空。 */
    private String indexErrorMessage;
    /** 最近一次索引成功时间，Unix 毫秒时间戳。 */
    private Long indexedAt;
    /** 最终回答实际引用该文档的累计次数。 */
    private Long referenceCount;
    /** 最近一次实际引用时间，Unix 毫秒时间戳。 */
    private Long lastReferencedAt;

    /** 当前文档已写入的向量分块数量。 */
    @ApiModelProperty(value = "分块数（预留）")
    private Integer chunkCount;

    /** 文档处理状态：0-未处理，1-处理中，2-处理完成。 */
    @ApiModelProperty(value = "状态：0-未处理，1-处理中，2-已完成")
    private Integer status;
}
