package com.aether.execution.controller;

import com.aether.entity.WebResponse;
import com.aether.execution.service.ExecutionService;
import com.aether.execution.vo.ExecutionVo;
import com.aether.execution.vo.ExecutionTraceSummaryVo;
import com.aether.permission.Permission;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Collectors;

@Api(tags = "Execution Trace API")
@Validated
@RestController
@Permission(path = "/agent/run")
@RequestMapping("/api/agent/execution")
public class ExecutionController {
    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @ApiOperation("按 Trace 查询执行树")
    @GetMapping("/trace/{traceId}")
    public WebResponse<List<ExecutionVo>> trace(@PathVariable @NotBlank String traceId) {
        if (StringUtils.isBlank(traceId)) return WebResponse.OK(java.util.Collections.emptyList());
        List<ExecutionVo> result = executionService.listByTraceId(traceId).stream().map(item -> {
            ExecutionVo vo = new ExecutionVo();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList());
        return WebResponse.OK(result);
    }

    @ApiOperation("查询 Trace 汇总指标")
    @GetMapping("/trace/{traceId}/summary")
    public WebResponse<ExecutionTraceSummaryVo> summary(@PathVariable @NotBlank String traceId) {
        return WebResponse.OK(executionService.summarize(traceId));
    }
}
