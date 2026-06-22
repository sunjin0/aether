package com.aether.sys.vo;

import  com.aether.sys.entity.Config;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ConfigVo extends Config {
private String key;
private List<ConfigVo> children;
    private Long current;
    private Long pageSize;
    }
