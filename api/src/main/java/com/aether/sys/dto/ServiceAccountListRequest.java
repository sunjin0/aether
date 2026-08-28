package com.aether.sys.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("服务账号列表请求")
public class ServiceAccountListRequest {
    @ApiModelProperty(value = "页码", example = "1") private Long current;
    @ApiModelProperty(value = "每页数量", example = "20") private Long pageSize;
    @ApiModelProperty(value = "应用 ID", example = "app-1") private String applicationId;
}
