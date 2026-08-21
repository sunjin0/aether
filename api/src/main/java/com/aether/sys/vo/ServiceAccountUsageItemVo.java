package com.aether.sys.vo;

import lombok.Data;

/**
 * 服务账号用量排行项。
 */
@Data
public class ServiceAccountUsageItemVo {
    private String id;
    private String name;
    private Long calls;
    private Long tokens;
}
