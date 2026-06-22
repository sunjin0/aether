package com.aether.msg.vo;

import  com.aether.msg.entity.Email;
import lombok.Data;
import lombok.EqualsAndHashCode;


@EqualsAndHashCode(callSuper = true)
@Data
public class EmailVo extends Email {
    private Long current;
    private Long pageSize;
}
