package com.aether.msg.controller;

import com.aether.permission.Permission;
import com.aether.entity.WebResponse;
import com.aether.msg.entity.Email;
import com.aether.msg.vo.EmailVo;
import com.aether.msg.dto.EmailRequests;
import com.aether.i18n.I18nUtils;
import com.aether.msg.service.EmailMessageService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.protobuf.ServiceException;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.BeanUtils;

import java.util.List;

/**
 * 提供Email相关的 REST 接口。
 */
@Api(value = "邮件服务 API")
@RestController
@Permission(path = "/msg/email")
@RequestMapping("/api/msg/email")
public class EmailController {
    private final EmailMessageService emailService;

    /**
     * 创建 {@code EmailController} 实例。
     */
    public EmailController(EmailMessageService emailService) {
        this.emailService = emailService;
    }

    /**
     * 邮件记录列表。
     */
    @ApiOperation("邮件记录列表")
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
            }
    )
    @PostMapping("/list")
    public WebResponse<List<Email>> list(@RequestBody EmailRequests.ListRequest request) throws ServiceException {
        EmailVo email = new EmailVo();
        BeanUtils.copyProperties(request, email);
        Page<Email> list = emailService.list(email);
        return WebResponse.Page(list.getRecords(), list.getTotal());
    }

    /**
     * 邮件信息。
     */
    @ApiOperation("邮件信息")
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header"),
                    @ApiImplicitParam(name = "id", required = true)
            }
    )
    @GetMapping("/info")
    public WebResponse<Email> info(@RequestParam("id") Long id) throws ServiceException {
        Email Email = emailService.getById(id);
        return WebResponse.OK(Email);
    }

    /**
     * 保存当前请求。
     */
    @ApiOperation("修改或保存")
    @Permission(path = "/msg/email", type = Permission.Type.Write)
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
            }
    )
    @PostMapping("/save")
    public WebResponse<Boolean> save(@RequestBody EmailRequests.SaveRequest request) throws ServiceException {
        Email message = new Email();
        BeanUtils.copyProperties(request, message);
        String id = message.getId();
        boolean save;
        if (StringUtils.isNotBlank(id)) {
            save = emailService.update(message);
            return WebResponse.OK(I18nUtils.getMessage(save ? "email.update.success" : "email.update.fail"), save);
        } else {
            save = emailService.save(message);
            return WebResponse.OK(I18nUtils.getMessage(save ? "email.create.success" : "email.create.fail"), save);
        }
    }

    /**
     * 删除当前请求。
     */
    @ApiOperation("删除")
    @Permission(path = "/msg/email", type = Permission.Type.Write)
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
            }
    )
    @GetMapping("/delete")
    public WebResponse<Boolean> delete(@RequestParam("id") String id) throws ServiceException {
        boolean delete = emailService.removeById(id);
        return WebResponse.OK(I18nUtils.getMessage(delete ? "email.delete.success" : "email.delete.fail"), delete);
    }
}
