package com.aether.agent.controller;

import com.aether.agent.skill.dto.AgentSkillDraftDto;
import com.aether.agent.skill.dto.AgentSkillPreviewDto;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillResource;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.agent.skill.service.SkillResourceWorkbenchService;
import com.aether.agent.skill.vo.AgentSkillDetailVo;
import com.aether.agent.skill.vo.AgentSkillPreviewVo;
import com.aether.agent.skill.vo.AgentSkillPublishCheckVo;
import com.aether.agent.skill.vo.AgentSkillVo;
import com.aether.agent.dto.AgentControllerRequests.SkillList;
import com.aether.agent.dto.AgentControllerRequests.Status;
import com.aether.entity.WebResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证智能体Skill控制器的行为。
 */
class AgentSkillControllerTest {

    private final AgentSkillService skillService = mock(AgentSkillService.class);
    private final SkillResourceWorkbenchService resourceWorkbenchService = mock(SkillResourceWorkbenchService.class);
    private final AgentSkillController controller = new AgentSkillController(skillService, resourceWorkbenchService);

    /**
     * 查询Returns分页查询。
     */
    @Test
    void listReturnsPage() {
        AgentSkill skill = skill("s1", "rule", "制度问答", 1);
        Page<AgentSkill> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(skill));
        page.setTotal(1);
        when(skillService.page(any(), any())).thenReturn(page);

        SkillList query = new SkillList();
        query.setCurrent(1L);
        query.setPageSize(10L);
        WebResponse<List<AgentSkillVo>> response = controller.list(query);

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("rule", response.getData().get(0).getCode());
    }

    /**
     * 详情ReturnsSkillWithDraft。
     */
    @Test
    void detailReturnsSkillWithDraft() {
        AgentSkillDetailVo detail = new AgentSkillDetailVo();
        detail.setSkill(skill("s1", "rule", "制度问答", 1));
        detail.setDraft(version("d1", "s1", null, 0));
        when(skillService.detail("s1")).thenReturn(detail);

        WebResponse<AgentSkillDetailVo> response = controller.detail("s1");

        assertEquals(200, response.getCode());
        assertNotNull(response.getData().getDraft());
    }

    /**
     * 创建ReturnsSkillId。
     */
    @Test
    void createReturnsSkillId() {
        when(skillService.createDraft(any())).thenReturn("s1");

        WebResponse<String> response = controller.create(new AgentSkillDraftDto());

        assertEquals("s1", response.getData());
        assertEquals(200, response.getCode());
    }

    /**
     * 更新DraftDelegates。
     */
    @Test
    void updateDraftDelegates() {
        WebResponse<Void> response = controller.updateDraft("s1", new AgentSkillDraftDto());

        assertEquals(200, response.getCode());
        verify(skillService).updateDraft("s1", new AgentSkillDraftDto());
    }

    /**
     * 创建下一个DraftReturnsDraftId。
     */
    @Test
    void createNextDraftReturnsDraftId() {
        when(skillService.createNextDraft("s1")).thenReturn("d2");

        WebResponse<String> response = controller.createNextDraft("s1");

        assertEquals("d2", response.getData());
    }

    /**
     * 发布OnlyAllows当前Draft。
     */
    @Test
    void publishOnlyAllowsCurrentDraft() {
        AgentSkillDetailVo detail = new AgentSkillDetailVo();
        detail.setDraft(version("d1", "s1", null, 0));
        when(skillService.detail("s1")).thenReturn(detail);
        when(skillService.publish("s1", null)).thenReturn(version("d1", "s1", 1, 1));

        WebResponse<AgentSkillVersion> response = controller.publish("s1", "d1");

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().getVersionNo());
    }

    /**
     * 发布RejectsNon当前Draft。
     */
    @Test
    void publishRejectsNonCurrentDraft() {
        AgentSkillDetailVo detail = new AgentSkillDetailVo();
        detail.setDraft(version("d1", "s1", null, 0));
        when(skillService.detail("s1")).thenReturn(detail);

        try {
            controller.publish("s1", "other");
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 预期拒绝非当前草稿
        }
    }

    /**
     * 处理versionsReturns历史记录。
     */
    @Test
    void versionsReturnsHistory() {
        when(skillService.listVersions("s1")).thenReturn(Arrays.asList(version("d1", "s1", null, 0), version("v1", "s1", 1, 1)));

        WebResponse<List<AgentSkillVersion>> response = controller.versions("s1");

        assertEquals(2, response.getData().size());
    }

    /**
     * 状态UpdatesSkill。
     */
    @Test
    void statusUpdatesSkill() {
        AgentSkill skill = skill("s1", "rule", "制度问答", 1);
        when(skillService.getById("s1")).thenReturn(skill);
        when(skillService.updateById(any())).thenReturn(true);

        Status dto = new Status();
        dto.setStatus(2);
        WebResponse<Void> response = controller.status("s1", dto);

        assertEquals(200, response.getCode());
        assertEquals(2, skill.getStatus());
    }

    /**
     * 上传资源ReturnsMetadata。
     */
    @Test
    void uploadResourceReturnsMetadata() throws Exception {
        AgentSkillResource resource = new AgentSkillResource();
        resource.setId("r1");
        resource.setType("MARKDOWN");
        resource.setObjectKey("skills/s1/d1/abc");
        resource.setContentSha256("abc");
        when(skillService.uploadResource(anyString(), anyString(), anyString(), any(), anyString(), any())).thenReturn(resource);

        MockMultipartFile file = new MockMultipartFile("file", "rules.md", "text/markdown", "hello".getBytes());
        WebResponse<AgentSkillResource> response = controller.uploadResource("s1", file, "参考规则", null);

        assertEquals(200, response.getCode());
        assertEquals("MARKDOWN", response.getData().getType());
    }

    /**
     * 处理resourcesReturnsDraftResources。
     */
    @Test
    void resourcesReturnsDraftResources() {
        AgentSkillResource resource = new AgentSkillResource();
        resource.setId("r1");
        when(skillService.listResources("s1")).thenReturn(Collections.singletonList(resource));

        WebResponse<List<AgentSkillResource>> response = controller.resources("s1");

        assertEquals(1, response.getData().size());
    }

    /**
     * 移除资源Delegates。
     */
    @Test
    void removeResourceDelegates() {
        WebResponse<Void> response = controller.removeResource("s1", "r1");

        assertEquals(200, response.getCode());
        verify(skillService).removeDraftResource("s1", "r1");
    }

    /**
     * 预览ReturnsPrompt。
     */
    @Test
    void previewReturnsPrompt() {
        AgentSkillPreviewVo preview = new AgentSkillPreviewVo();
        preview.setPrompt("[Installed Skill]");
        preview.setEstimatedTokens(10L);
        when(skillService.preview(anyString(), any())).thenReturn(preview);

        WebResponse<AgentSkillPreviewVo> response = controller.preview("s1", new AgentSkillPreviewDto());

        assertEquals(200, response.getCode());
        assertNotNull(response.getData().getPrompt());
    }

    /**
     * 发布检查ReturnsBlockersAndWarnings。
     */
    @Test
    void publishCheckReturnsBlockersAndWarnings() {
        AgentSkillPublishCheckVo check = new AgentSkillPublishCheckVo();
        check.setReady(false);
        check.getBlockers().add("请填写系统指令");
        when(skillService.publishCheck("s1")).thenReturn(check);

        WebResponse<AgentSkillPublishCheckVo> response = controller.publishCheck("s1");

        assertEquals(200, response.getCode());
        assertEquals("请填写系统指令", response.getData().getBlockers().get(0));
    }

    /**
     * 处理skill。
     */
    private AgentSkill skill(String id, String code, String name, int status) {
        AgentSkill skill = new AgentSkill();
        skill.setId(id);
        skill.setCode(code);
        skill.setName(name);
        skill.setStatus(status);
        return skill;
    }

    /**
     * 处理version。
     */
    private AgentSkillVersion version(String id, String skillId, Integer versionNo, int status) {
        AgentSkillVersion version = new AgentSkillVersion();
        version.setId(id);
        version.setSkillId(skillId);
        version.setVersionNo(versionNo);
        version.setStatus(status);
        return version;
    }
}
