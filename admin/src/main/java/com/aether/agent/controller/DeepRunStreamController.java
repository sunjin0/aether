package com.aether.agent.controller;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentRunStep;
import com.aether.agent.service.AgentRunStepService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.exception.ServerException;
import com.aether.local.CurrentUser;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Authenticated Deep Run stream: persisted history first, followed by live callback events. */
@RestController
@RequestMapping("/api/agent/deep-runs")
public class DeepRunStreamController {
    private static final long TIMEOUT_MS = 15 * 60 * 1000L;
    private final DeepAgentRunService deepAgentRunService;
    private final AgentRunStepService agentRunStepService;

    public DeepRunStreamController(DeepAgentRunService deepAgentRunService, AgentRunStepService agentRunStepService) {
        this.deepAgentRunService = deepAgentRunService;
        this.agentRunStepService = agentRunStepService;
    }

    @GetMapping(value = "/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String runId) throws Exception {
        AgentRun run = deepAgentRunService.getDeepRunForReconciliation(runId);
        String userId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId");
        if (StringUtils.isBlank(userId) || !userId.equals(run.getUserId())) {
            throw new ServerException(403, "无权访问该 Deep Agent 运行");
        }
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitter.send(SseEmitter.event().comment("connected"));
        DeepRunEventHub.add(runId, emitter);
        for (AgentRunStep step : agentRunStepService.listByRunId(runId)) {
            if ("message.delta".equals(step.getEventType())) {
                continue;
            }
            emitter.send(SseEmitter.event().name("run_step").data(stepJson(step)));
        }
        return emitter;
    }

    private String stepJson(AgentRunStep step) {
        JSONObject data = new JSONObject();
        data.put("runId", step.getRunId());
        data.put("eventId", step.getEventId());
        data.put("eventType", step.getEventType());
        data.put("occurredAt", step.getOccurredAt());
        data.put("data", step.getData());
        return data.toJSONString();
    }
}
