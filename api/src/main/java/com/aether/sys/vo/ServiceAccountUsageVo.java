package com.aether.sys.vo;

import lombok.Data;

import java.util.List;

/**
 * 服务账号外部接入使用情况。
 */
@Data
public class ServiceAccountUsageVo {
    private Integer rangeDays;
    private Long serviceAccountCount;
    private Long totalCalls;
    private Long agentCalls;
    private Long workflowStarts;
    private Long totalTokens;
    private Long last24HoursCalls;
    private Long serviceAccountDelta;
    private Double serviceAccountGrowthRate;
    private Long totalCallsDelta;
    private Double totalCallsGrowthRate;
    private Long totalTokensDelta;
    private Double totalTokensGrowthRate;
    private Long last24HoursCallsDelta;
    private Double last24HoursCallsGrowthRate;
    private Long accountTotal;
    private Long agentTotal;
    private Long workflowTotal;
    private List<ServiceAccountUsageItemVo> accounts;
    private List<ServiceAccountUsageItemVo> agents;
    private List<ServiceAccountUsageItemVo> workflows;
}
