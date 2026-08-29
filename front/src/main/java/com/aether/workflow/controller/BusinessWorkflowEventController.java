package com.aether.workflow.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.service.ServiceAccountService;
import com.aether.workflow.dto.AgentWorkflowEventDto;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 面向服务账号的通用工作流事件入口。 */
@Api(tags = "业务工作流事件 API")
@RestController
@RequestMapping("/api/business/workflow-events")
public class BusinessWorkflowEventController {
    private final ServiceAccountService serviceAccountService;
    private final AgentWorkflowExecutionService executionService;

    public BusinessWorkflowEventController(ServiceAccountService serviceAccountService, AgentWorkflowExecutionService executionService) {
        this.serviceAccountService = serviceAccountService;
        this.executionService = executionService;
    }

    @ApiOperation("提交业务事件并唤醒匹配工作流")
    @PostMapping("/{eventType}")
    public WebResponse<Integer> signal(@PathVariable String eventType, @RequestBody(required = false) AgentWorkflowEventDto request) {
        ServiceAccount account = currentAccount();
        int resumed = executionService.signalEventByType(account.getApplicationId(), eventType, request, principalId());
        return WebResponse.OK(null, resumed);
    }

    private ServiceAccount currentAccount() {
        Map<String, String> user = CurrentUser.getUser();
        String accountId = user == null ? null : user.get("serviceAccountId");
        if (StringUtils.isBlank(accountId)) throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
        ServiceAccount account = serviceAccountService.getById(accountId);
        if (account == null || Boolean.TRUE.equals(account.getDeleted())) throw new ServerException(404, I18nUtils.getMessage("service-account.not-found"));
        if (!Boolean.TRUE.equals(account.getEnabled())) throw new ServerException(403, I18nUtils.getMessage("service-account.disabled"));
        return account;
    }

    private String principalId() {
        Map<String, String> user = CurrentUser.getUser();
        String id = user == null ? null : user.get("principalId");
        if (StringUtils.isBlank(id)) id = user == null ? null : user.get("userId");
        if (StringUtils.isBlank(id)) throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
        return id;
    }
}
