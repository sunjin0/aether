package com.aether.agent.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operational aggregate for context assembly, compression and pressure signals.
 */
@Data
public class AgentContextOperationsMetricsVo {
    @ApiModelProperty(value = "Lower created_at bound used for the aggregate")
    private Long sinceCreatedAt;
    @ApiModelProperty(value = "Total metric snapshots")
    private Long totalMetricCount = 0L;
    @ApiModelProperty(value = "Final answer or Deep step snapshots")
    private Long completedRequestMetricCount = 0L;
    @ApiModelProperty(value = "Average input occupancy percentage for completed answer or Deep step calls")
    private Double averageOccupancyPercent = 0D;
    @ApiModelProperty(value = "Completed answer or Deep step calls whose input occupancy is at least 80%")
    private Long highPressureMetricCount = 0L;
    @ApiModelProperty(value = "Compression model call snapshots")
    private Long compressionMetricCount = 0L;
    @ApiModelProperty(value = "Compression snapshots marked as completed")
    private Long compressionCompletedCount = 0L;
    @ApiModelProperty(value = "Compression snapshots marked as failed fallback")
    private Long compressionFailedFallbackCount = 0L;
    @ApiModelProperty(value = "Compression snapshots marked as async pending")
    private Long compressionPendingCount = 0L;
    @ApiModelProperty(value = "Metric snapshots with any trimmed history messages")
    private Long trimmedMetricCount = 0L;
    @ApiModelProperty(value = "Total trimmed history message count")
    private Long trimmedMessageCount = 0L;
    @ApiModelProperty(value = "Metric snapshots with any compressed messages")
    private Long compressedMetricCount = 0L;
    @ApiModelProperty(value = "Total compressed message count")
    private Long compressedMessageCount = 0L;
    @ApiModelProperty(value = "Whether compression latency is available in current metric schema")
    private Boolean latencyAvailable = false;
    @ApiModelProperty(value = "Metric count by call type")
    private Map<String, Long> byCallType = new LinkedHashMap<String, Long>();
    @ApiModelProperty(value = "Metric count by compression status")
    private Map<String, Long> byCompressionStatus = new LinkedHashMap<String, Long>();
}
