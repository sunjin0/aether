package com.aether.sys.vo;

import lombok.Data;

import java.util.List;

/**
 * 服务账号外部接入使用情况。
 */
@Data
public class ServiceAccountUsageVo {
    private Long totalCalls;
    private Long agentCalls;
    private Long workflowStarts;
    private Long totalTokens;
    private List<ServiceAccountUsageItemVo> accounts;
    private List<ServiceAccountUsageItemVo> agents;
    private List<ServiceAccountUsageItemVo> workflows;
}
