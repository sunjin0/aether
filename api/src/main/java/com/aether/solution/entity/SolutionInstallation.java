package com.aether.solution.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Solution 与业务 Application 的安装关系；卸载只改变状态，不删除历史数据。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aether_solution_installation")
public class SolutionInstallation extends AccountOwnedEntity {
    /** 安装关系所属租户。 */
    private String solutionId;
    private String applicationId;
    private String solutionVersion;
    private Integer status;
}
