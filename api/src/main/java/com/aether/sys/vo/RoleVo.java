package com.aether.sys.vo;

import  com.aether.sys.entity.Role;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class RoleVo extends Role {

    private Long current;
    private Long pageSize;
}
