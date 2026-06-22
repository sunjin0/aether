package com.aether.user.vo;

import  com.aether.user.entity.Member;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemberVo extends Member {
    private Long current;
    private Long pageSize;
}
