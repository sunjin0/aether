package com.aether.sys.dto;

import lombok.Data;
import java.util.List;

/** 服务账号可维护字段；客户端 ID 与密钥不可通过编辑接口变更。 */
@Data
public class ServiceAccountUpdateDto {
    private String name;
    private String description;
    private List<String> roleIds;
    private List<String> allowedWorkflowIds;
    private Integer maxStartsPerHour;
}
