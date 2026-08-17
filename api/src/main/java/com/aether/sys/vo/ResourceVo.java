package com.aether.sys.vo;

import com.aether.sys.entity.Resource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 表示资源VO。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ResourceVo extends Resource {
    /**
     * 标题
     */
    private String title;
    /**
     * 页面权限
     */
    private String access;
    /**
     * 钥匙
     */
    private String key;
    /**
     * 孩子
     */
    private List<ResourceVo> children;
    private Long current;
    private Long pageSize;

}
