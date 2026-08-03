package com.aether.sys.controller;



import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aether.permission.Permission;
import com.aether.sys.service.ResourceService;
import com.aether.entity.Option;
import com.aether.entity.WebResponse;
import com.aether.sys.entity.Resource;
import com.aether.enums.ResourceType;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.validator.ValidEntity;
import com.aether.sys.vo.ResourceVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * <p>
 * 资源表 前端控制器
 * </p>
 *
 * @author sun
 * @since 2024-11-12
 */
@Api("系统资源服务 API")
@Validated
@RestController
@Permission(path = "/sys/resource")
@RequestMapping("/api/sys/resource")
public class ResourceController {
    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @ApiOperation(value = "根据id获取资源信息")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/info")
    public WebResponse<Resource> info(@RequestParam @NotBlank String id) throws ServerException {
        return WebResponse.OK(resourceService.getById(id));
    }

    @ApiOperation(value = "获取资源列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<ResourceVo>> list(@RequestBody ResourceVo Resource) throws ServerException {
        Page<ResourceVo> list = resourceService.list(Resource);
        return WebResponse.Page(list.getRecords(), list.getTotal());
    }

    @ApiOperation(value = "保存资源")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/resource", type = Permission.Type.Write)
    @PostMapping("/add")
    public WebResponse<Boolean> save(@RequestBody
                                     @ValidEntity(fieldNames = {"name", "nameCn", "type"})
                                     Resource resource) throws ServerException {
        boolean save = resourceService.save(resource,resource.getLeaf());
        return WebResponse.OK(I18nUtils.getMessage(save ? "system.resource.create.success" : "system.resource.create.fail"), save);
    }
    @ApiOperation(value = "更新资源")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/resource", type = Permission.Type.Write)
    @PostMapping("/update")
    public WebResponse<Boolean> update(@RequestBody
                                      @ValidEntity(fieldNames = {"name", "nameCn", "type"})
                                           Resource resource){
        boolean update = resourceService.updateById(resource);
        return WebResponse.OK(I18nUtils.getMessage(update ? "system.resource.update.success" : "system.resource.update.fail"), update);
    }

    @ApiOperation(value = "删除资源")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/resource", type = Permission.Type.Write)
    @GetMapping("/delete")
    public WebResponse<Boolean> delete(@RequestParam @NotBlank String id) throws ServerException {
        List<Resource> list = resourceService.list(Wrappers.lambdaQuery(Resource.class));
        Stack<Resource> stack = new Stack<>();
        ArrayList<String> ids = new ArrayList<>();
        ids.add(id);
        @NotBlank String finalId = id;
        stack.push(list.stream().filter(v -> v.getId().equals(finalId)).findFirst().orElse(null));
        while (!stack.isEmpty()) {
            Resource resource = stack.pop();
            if (resource != null) {
                id = resource.getId();
                ids.add(id);
                @NotBlank String finalId1 = id;
                List<Resource> children = list.stream().filter(v -> v.getParentId().toString().equals(finalId1)).collect(Collectors.toList());
                stack.addAll(children);
            }
        }
        boolean b = resourceService.remove(Wrappers.lambdaUpdate(Resource.class)
                .in(Resource::getId, ids));
        return WebResponse.OK(I18nUtils.getMessage(b ? "system.resource.delete.success" : "system.resource.delete.fail"), b);
    }

    @ApiOperation(value = "下拉数据")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/select")
    public WebResponse<List<Option>> select() throws ServerException {
        List<Resource> list = resourceService.list(Wrappers.lambdaQuery(Resource.class)
                .eq(Resource::getType, ResourceType.ROUTE.getCode())
                .orderByAsc(Resource::getSortNum));
        List<Option> options = list.stream().map(resource -> {
            Option option = new Option();
            String lng = I18nUtils.getMessage("lng");
            option.setLabel(lng.equals("en_US") ? resource.getName() : resource.getNameCn());
            option.setValue(resource.getId());
            return option;
        }).collect(Collectors.toList());
        options.add(0, new Option(I18nUtils.getMessage("system.resource.not.parent"),"0"));
        return WebResponse.OK(options);
    }


}
