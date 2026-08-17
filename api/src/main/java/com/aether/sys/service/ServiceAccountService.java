package com.aether.sys.service;

import com.aether.auth.ServiceTokenVerifier;
import com.aether.sys.dto.ServiceAccountCreateDto;
import com.aether.sys.dto.ServiceAccountTokenDto;
import com.aether.sys.dto.ServiceAccountUpdateDto;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.vo.ServiceAccountSecretVo;
import com.aether.sys.vo.ServiceAccountTokenVo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 管理服务账号、密钥和访问令牌。
 */
public interface ServiceAccountService extends IService<ServiceAccount>, ServiceTokenVerifier {
    /**
     * 创建服务账号，并仅在本次调用中返回账号密钥。
     */
    ServiceAccountSecretVo create(ServiceAccountCreateDto dto);

    /**
     * 更新指定服务账号的名称、权限范围和配额配置。
     */
    boolean update(String id, ServiceAccountUpdateDto dto);

    /**
     * 删除指定服务账号。
     */
    boolean delete(String id);

    /**
     * 轮换服务账号密钥，并仅在本次调用中返回新密钥。
     */
    ServiceAccountSecretVo rotateSecret(String id);

    /**
     * 为服务账号签发具有效期和声明信息的访问令牌。
     */
    ServiceAccountTokenVo issueToken(ServiceAccountTokenDto dto);

    /**
     * 启用或停用指定服务账号。
     */
    boolean setEnabled(String id, boolean enabled);

    /**
     * 校验调用账号对工作流的范围和额度，并记录一次业务启动额度。
     */
    void assertWorkflowStartAllowed(String id, String workflowId);
}
