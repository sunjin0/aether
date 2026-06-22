package com.aether.agent.controller;

import com.aether.agent.dto.AgentToolDto;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.vo.AgentToolVo;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具管理 Controller
 */
@Api(tags = "工具管理 API")
@Validated
@RestController
@Permission(path = "/agent/tool")
@RequestMapping("/api/agent/tool")
public class AgentToolController {

    private final AgentToolService agentToolService;

    @Autowired
    public AgentToolController(AgentToolService agentToolService) {
        this.agentToolService = agentToolService;
    }

    @ApiOperation("工具列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<AgentToolVo>> list(@RequestBody AgentToolVo vo) {
        Page<AgentTool> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        Wrapper<AgentTool> wrapper = Wrappers.lambdaQuery(AgentTool.class)
                .like(StringUtils.isNotBlank(vo.getName()), AgentTool::getName, vo.getName())
                .like(StringUtils.isNotBlank(vo.getCode()), AgentTool::getCode, vo.getCode())
                .eq(StringUtils.isNotBlank(vo.getType()), AgentTool::getType, vo.getType())
                .eq(vo.getStatus() != null, AgentTool::getStatus, vo.getStatus())
                .eq(AgentTool::getDeleted, false)
                .orderByDesc(AgentTool::getCreatedAt);
        Page<AgentTool> result = agentToolService.page(page, wrapper);
        List<AgentToolVo> list = result.getRecords().stream().map(item -> {
            AgentToolVo itemVo = new AgentToolVo();
            BeanUtils.copyProperties(item, itemVo);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    @ApiOperation("工具详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工具ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}")
    public WebResponse<AgentToolVo> detail(@PathVariable @NotBlank String id) {
        AgentTool tool = agentToolService.getById(id);
        if (tool == null || Boolean.TRUE.equals(tool.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        AgentToolVo vo = new AgentToolVo();
        BeanUtils.copyProperties(tool, vo);
        return WebResponse.OK(vo);
    }

    @ApiOperation("新增工具")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/tool", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping
    public WebResponse<String> save(@RequestBody AgentToolDto dto) {
        AgentTool tool = new AgentTool();
        BeanUtils.copyProperties(dto, tool);
        boolean saved = agentToolService.save(tool);
        return WebResponse.OK(saved ? I18nUtils.getMessage("add.success") : I18nUtils.getMessage("add.fail"), tool.getId());
    }

    @ApiOperation("编辑工具")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/tool", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable @NotBlank String id, @RequestBody AgentToolDto dto) {
        AgentTool tool = new AgentTool();
        BeanUtils.copyProperties(dto, tool);
        tool.setId(id);
        boolean updated = agentToolService.updateById(tool);
        return WebResponse.OK(updated ? I18nUtils.getMessage("update.success") : I18nUtils.getMessage("update.fail"));
    }

    @ApiOperation("删除工具")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工具ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/tool", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        boolean removed = agentToolService.removeById(id);
        return WebResponse.OK(removed ? I18nUtils.getMessage("delete.success") : I18nUtils.getMessage("delete.fail"));
    }

    @ApiOperation("测试工具")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/tool", type = Permission.Type.Write)
    @PostMapping("/{id}/test")
    public WebResponse<String> testTool(@PathVariable @NotBlank String id, @RequestBody String params) {
        // TODO: V0.5 实现工具执行器后完善
        return WebResponse.OK("工具测试待实现");
    }
}
