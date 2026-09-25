package com.aether.solution.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 可安装的业务解决方案模板。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aether_solution")
public class Solution extends AccountOwnedEntity {
    private String name;
    private String code;
    private String version;
    private String description;
    private String manifestJson;
    private Integer status;
}
