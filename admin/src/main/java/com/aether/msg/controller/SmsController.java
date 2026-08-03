package com.aether.msg.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.protobuf.ServiceException;
import com.aether.permission.Permission;
import com.aether.entity.WebResponse;
import com.aether.msg.entity.Sms;
import com.aether.msg.vo.SmsVo;
import com.aether.i18n.I18nUtils;
import com.aether.msg.service.SmsMessageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(value = "短信服务 API")
@RestController
@Permission(path = "/msg/sms")
@RequestMapping("/api/msg/sms")
public class SmsController {
    private final SmsMessageService smsService;

    public SmsController(SmsMessageService smsService) {
        this.smsService = smsService;
    }

    @ApiOperation("短信记录列表")
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
            }
    )
    @PostMapping("/list")
    public WebResponse<List<Sms>> list(@RequestBody SmsVo sms) {
        Page<Sms> list = smsService.list(sms);
        return WebResponse.Page(list.getRecords(), list.getTotal());
    }

    @ApiOperation("短信信息")
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header"),
                    @ApiImplicitParam(name = "id", required = true)
            }
    )
    @GetMapping("/info")
    public WebResponse<Sms> info(@RequestParam("id") String id) throws ServiceException {
        return WebResponse.OK(smsService.getById(id));
    }

    @ApiOperation("修改或保存")
    @Permission(path = "/msg/sms", type = Permission.Type.Write)
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
            }
    )
    @PostMapping("/save")
    public WebResponse<Boolean> save(@RequestBody Sms message) throws ServiceException {
        String id = message.getId();
        boolean save = smsService.save(message);
        if (StringUtils.isNotEmpty(id)) {
            save = smsService.updateById(message);
            return WebResponse.OK(I18nUtils.getMessage(save ? "sms.update.success" : "sms.update.fail"), save);
        }
        return WebResponse.OK(I18nUtils.getMessage(save ? "sms.create.success" : "sms.create.fail"), save);
    }

    @ApiOperation("删除")
    @Permission(path = "/msg/sms", type = Permission.Type.Write)
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header"),
                    @ApiImplicitParam(name = "id", required = true)
            }
    )
    @PostMapping("/delete")
    public WebResponse<Boolean> delete(@RequestParam("id") String id) throws ServiceException {
        Boolean delete = smsService.delete(id);
        return WebResponse.OK(I18nUtils.getMessage(delete ? "sms.delete.success" : "sms.delete.fail"), delete);
    }
}
