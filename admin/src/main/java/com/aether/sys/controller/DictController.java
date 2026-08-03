package com.aether.sys.controller;

import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aether.permission.Permission;
import com.aether.sys.service.DictService;
import com.aether.entity.Option;
import com.aether.entity.WebResponse;
import com.aether.sys.entity.Dict;
import com.aether.i18n.I18nUtils;
import com.aether.validator.ValidEntity;
import com.aether.sys.vo.DictVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.stream.Collectors;

@Api(value = "系统字典服务 API")
@Validated
@RestController
@Permission(path = "/sys/dict")
@RequestMapping("/api/sys/dict")
public class DictController {
    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @ApiOperation(value = "获取字典列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<DictVo>> list(@RequestBody DictVo dict) {
        Page<DictVo> list = dictService.list(dict);
        return WebResponse.Page(list.getRecords(), list.getTotal());
    }

    @ApiOperation("获取字典")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/info")
    public WebResponse<Dict> info(@RequestParam @NotNull String id) {
        return WebResponse.OK(dictService.info(id));
    }

    @ApiOperation("删除字典")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/dict", type = Permission.Type.Write)
    @GetMapping("/delete")
    public WebResponse<Boolean> delete(@RequestParam @NotNull String id) {
        boolean delete = dictService.delete(id);
        return WebResponse.OK(delete ? I18nUtils.getMessage("system.dict.delete.success") : I18nUtils.getMessage("system.dict.delete.fail"), delete);
    }

    @ApiOperation("添加字典")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/dict", type = Permission.Type.Write)
    @PostMapping("/add")
    public WebResponse<Boolean> save(@RequestBody
                                     @ValidEntity(fieldNames = {"code", "name", "nameCn"})
                                     Dict dict) {
        boolean save = dictService.save(dict);
            return WebResponse.OK(I18nUtils.getMessage(save ? "system.dict.create.success" : "system.dict.create.fail"), save);
    }
    @ApiOperation("修改字典")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/dict", type = Permission.Type.Write)
    @PostMapping("/update")
    public WebResponse<Boolean> update(@RequestBody
                                      @ValidEntity(fieldNames = {"code", "name", "nameCn"})
                                      Dict dict) {
        boolean update = dictService.updateById(dict);
        return WebResponse.OK(I18nUtils.getMessage(update ? "system.dict.update.success" : "system.dict.update.fail"), update);
    }

    @ApiOperation("获取字典选项")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "parentCode", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/select")
    public WebResponse<List<Option>> getOptions() {
        List<Dict> list = dictService.select();
        String lng = I18nUtils.getMessage("lng");
        List<Option> options = list.stream().map(item -> new Option("en_US".equals(lng) ? item.getName() : item.getNameCn(), item.getCode())).collect(Collectors.toList());
        return WebResponse.OK(options);
    }

    @ApiOperation("根据code获取字典,可指定语言")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "code", required = true),
            @ApiImplicitParam(name = "lang"),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(required = false)
    @GetMapping("/code")
    public WebResponse<Dict> getByCode(@RequestParam @NotNull String code, @RequestParam @Nullable String lang) {
        return WebResponse.OK(dictService.getByCode(code, lang));
    }

    @ApiOperation("根据父code获取字典,可指定语言")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "parentCode", required = true),
            @ApiImplicitParam(name = "lang"),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(required = false)
    @GetMapping("/options")
    public WebResponse<List<Option>> getByParentCode(@RequestParam @NotNull String parentCode, @RequestParam @Nullable Boolean useValue) {
        return WebResponse.OK(dictService.getOptions(parentCode, useValue));
    }
}
