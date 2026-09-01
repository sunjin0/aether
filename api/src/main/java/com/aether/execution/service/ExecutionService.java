package com.aether.execution.service;

import com.aether.execution.entity.Execution;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import com.aether.execution.vo.ExecutionTraceSummaryVo;

public interface ExecutionService extends IService<Execution> {
    List<Execution> listByTraceId(String traceId);
    ExecutionTraceSummaryVo summarize(String traceId);
    Execution start(String type, String traceId, String parentId, String actorId, String resourceId);
    boolean finish(String id, String status, String errorCode, String errorMessage);
}
