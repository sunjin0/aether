package com.aether.evaluation.entity;
import com.aether.entity.BaseEntity; import com.aether.evaluation.handler.JsonbStringTypeHandler; import com.baomidou.mybatisplus.annotation.TableField; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName(value = "evaluation_evaluator_version", autoResultMap = true)
public class EvaluationEvaluatorVersion extends BaseEntity { private String evaluatorId, contentHash, publishedBy; @TableField(typeHandler = JsonbStringTypeHandler.class) private String configJson; private Integer versionNo; private Long publishedAt; }
