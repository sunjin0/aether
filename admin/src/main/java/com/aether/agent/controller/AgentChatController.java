package com.aether.agent.controller;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.entity.WebResponse;
import com.aether.permission.Permission;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent聊天 Controller。
 */
@Api(tags = "Agent聊天 API")
@Validated
@RestController
@Permission(path = "/agent/chat")
@RequestMapping("/api/agent/chat")
public class AgentChatController {

    private final AgentChatService agentChatService;

    @Autowired
    public AgentChatController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @ApiOperation("非流式聊天")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping
    public WebResponse<AgentMessageVo> chat(@RequestBody AgentChatDto dto) {
        return WebResponse.OK(agentChatService.chat(dto));
    }
}
