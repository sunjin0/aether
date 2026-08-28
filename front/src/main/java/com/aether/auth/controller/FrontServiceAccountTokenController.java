package com.aether.auth.controller;

import com.aether.entity.WebResponse;
import com.aether.i18n.I18nUtils;
import com.aether.sys.dto.ServiceAccountTokenDto;
import com.aether.sys.service.ServiceAccountService;
import com.aether.sys.vo.ServiceAccountTokenVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

/**
 * Front service-account client-credentials entry point.
 * This route intentionally has no administrator permission requirement: the
 * client ID and secret themselves are the machine credential.
 */
@Api(tags = "Front 服务账号认证 API")
@RestController
public class FrontServiceAccountTokenController {
    private final ServiceAccountService serviceAccountService;

    public FrontServiceAccountTokenController(ServiceAccountService serviceAccountService) {
        this.serviceAccountService = serviceAccountService;
    }

    @ApiOperation(value = "签发 Front 业务接口访问令牌",
            notes = "传入服务账号 clientId/clientSecret。成功后使用 Authorization: Bearer <accessToken> 调用 /api/business/**。")
    @PostMapping("/api/auth/service-account/token")
    public WebResponse<ServiceAccountTokenVo> token(@RequestBody ServiceAccountTokenDto dto, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        return WebResponse.OK(I18nUtils.getMessage("service-account.token.issue.success"), serviceAccountService.issueToken(dto));
    }
}
