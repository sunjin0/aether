package com.aether.sys.service;

import com.aether.auth.ServiceTokenVerifier;
import com.aether.sys.dto.ServiceAccountCreateDto;
import com.aether.sys.dto.ServiceAccountTokenDto;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.vo.ServiceAccountSecretVo;
import com.aether.sys.vo.ServiceAccountTokenVo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ServiceAccountService extends IService<ServiceAccount>, ServiceTokenVerifier {
    ServiceAccountSecretVo create(ServiceAccountCreateDto dto);
    ServiceAccountSecretVo rotateSecret(String id);
    ServiceAccountTokenVo issueToken(ServiceAccountTokenDto dto);
    boolean setEnabled(String id, boolean enabled);
    /** 校验调用账号对工作流的范围和额度，并记录一次业务启动额度。 */
    void assertWorkflowStartAllowed(String id, String workflowId);
}
