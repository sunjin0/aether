package com.aether.agent.controller;

import com.aether.agent.skill.dto.AgentSkillBindingUpdateDto;
import com.aether.agent.skill.dto.AgentSkillInstallDto;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.entity.WebResponse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证智能体DefinitionSkillBinding控制器的行为。
 */
class AgentDefinitionSkillBindingControllerTest {

    private final AgentSkillService skillService = mock(AgentSkillService.class);
    private final AgentDefinitionSkillBindingController controller = new AgentDefinitionSkillBindingController(skillService);

    /**
     * 查询ReturnsBindings。
     */
    @Test
    void listReturnsBindings() {
        AgentDefinitionSkillBinding binding = new AgentDefinitionSkillBinding();
        binding.setId("b1");
        binding.setAgentDefinitionId("a1");
        binding.setSkillVersionId("v1");
        when(skillService.listBindings("a1")).thenReturn(Arrays.asList(binding));

        WebResponse<List<AgentDefinitionSkillBinding>> response = controller.list("a1");

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("v1", response.getData().get(0).getSkillVersionId());
    }

    /**
     * 处理installReturnsBindingId。
     */
    @Test
    void installReturnsBindingId() {
        when(skillService.install(eq("a1"), any(AgentSkillInstallDto.class))).thenReturn("b1");

        AgentSkillInstallDto dto = new AgentSkillInstallDto();
        dto.setSkillVersionId("v1");
        WebResponse<String> response = controller.install("a1", dto);

        assertEquals(200, response.getCode());
        assertEquals("b1", response.getData());
    }

    /**
     * 更新Delegates。
     */
    @Test
    void updateDelegates() {
        AgentSkillBindingUpdateDto dto = new AgentSkillBindingUpdateDto();
        dto.setPriority(2);

        WebResponse<Void> response = controller.update("a1", "b1", dto);

        assertEquals(200, response.getCode());
        verify(skillService).updateBinding("a1", "b1", dto);
    }

    /**
     * 删除Delegates。
     */
    @Test
    void deleteDelegates() {
        WebResponse<Void> response = controller.delete("a1", "b1");

        assertEquals(200, response.getCode());
        verify(skillService).removeBinding("a1", "b1");
    }
}
