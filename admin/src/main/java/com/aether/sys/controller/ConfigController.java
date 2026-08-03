package com.aether.sys.controller;

import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aether.permission.Permission;
import com.aether.sys.service.ConfigService;
import com.aether.entity.WebResponse;
import com.aether.sys.entity.Config;
import com.aether.i18n.I18nUtils;
import com.aether.validator.ValidEntity;
import com.aether.sys.vo.ConfigVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;

@Api(value = "系统配置服务 API")
@Validated
@RestController
@Permission(path = "/sys/config")
@RequestMapping("/api/sys/config")
public class ConfigController {
    private final ConfigService configService;
    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @ApiOperation(value = "获取配置列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<ConfigVo>> list(@RequestBody ConfigVo config) {
        Page<ConfigVo> list = configService.list(config);
        return WebResponse.Page(list.getRecords(), list.getTotal());
    }
    @ApiOperation("获取配置")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/info")
    public WebResponse<Config> info(@RequestParam @NotNull String id) {
        return WebResponse.OK(configService.info(id));
    }

    @ApiOperation("删除配置")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/config", type = Permission.Type.Write)
    @GetMapping("/delete")
    public WebResponse<Boolean> delete(@RequestParam @NotNull String id) {
        boolean delete = configService.delete(id);
        return WebResponse.OK(delete ? I18nUtils.getMessage("system.config.delete.success") : I18nUtils.getMessage("system.config.delete.fail"), delete);
    }

    @ApiOperation("添加配置")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/config", type = Permission.Type.Write)
    @PostMapping("/add")
    public WebResponse<Boolean> save(@RequestBody
                                     @ValidEntity(fieldNames = {"code", "name"})
                                         Config config) {
        boolean save = configService.save(config);
            return WebResponse.OK(I18nUtils.getMessage(save ? "system.config.create.success" : "system.config.create.fail"), save);
    }
    @ApiOperation("修改字典")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/config", type = Permission.Type.Write)
    @PostMapping("/update")
    public WebResponse<Boolean> update(@RequestBody
                                      @ValidEntity(fieldNames = {"code", "name"})
                                           Config config) {
        boolean update = configService.updateById(config);
        return WebResponse.OK(I18nUtils.getMessage(update ? "system.config.update.success" : "system.config.update.fail"), update);
    }
}
