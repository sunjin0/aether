package com.aether.solution.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 可安装的业务解决方案模板。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aether_solution")
public class Solution extends BaseEntity {
    /** 所属租户；为空时兼容历史单租户数据。 */
    private String tenantId;
    private String name;
    private String code;
    private String version;
    private String description;
    private String manifestJson;
    private Integer status;
}
