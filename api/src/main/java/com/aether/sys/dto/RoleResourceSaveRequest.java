package com.aether.sys.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.util.List;

@Data
@ApiModel("角色资源保存请求")
public class RoleResourceSaveRequest {
    @ApiModelProperty(value = "角色 ID", required = true, example = "1") private String roleId;
    @ApiModelProperty(value = "资源 ID 列表", required = true, example = "[\"1\", \"2\"]") private List<String> resourceIds;
}
