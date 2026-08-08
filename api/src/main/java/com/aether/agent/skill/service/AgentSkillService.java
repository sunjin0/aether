package com.aether.agent.skill.service;

import com.aether.agent.skill.dto.AgentSkillBindingUpdateDto;
import com.aether.agent.skill.dto.AgentSkillDraftDto;
import com.aether.agent.skill.dto.AgentSkillInstallDto;
import com.aether.agent.skill.dto.AgentSkillPreviewDto;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillResource;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.vo.AgentSkillDetailVo;
import com.aether.agent.skill.vo.AgentSkillPreviewVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/** Skill 草稿、发布和 Agent 安装生命周期服务。 */
public interface AgentSkillService extends IService<AgentSkill> {
    String createDraft(AgentSkillDraftDto dto);
    String createNextDraft(String skillId);
    void updateDraft(String skillId, AgentSkillDraftDto dto);
    AgentSkillVersion publish(String skillId, String userId);
    AgentSkillDetailVo detail(String skillId);
    List<AgentSkillVersion> listVersions(String skillId);
    List<AgentDefinitionSkillBinding> listBindings(String agentId);
    String install(String agentId, AgentSkillInstallDto dto);
    void updateBinding(String agentId, String bindingId, AgentSkillBindingUpdateDto dto);
    void removeBinding(String agentId, String bindingId);

    /** 上传资源到 Skill 草稿版本；content 为文件字节，fileName 用于类型判定。 */
    AgentSkillResource uploadResource(String skillId, String fileName, String contentType, byte[] content, String purpose, String type);
    /** 资源列表：有草稿时返回草稿资源，否则返回当前发布版本资源。 */
    List<AgentSkillResource> listResources(String skillId);
    /** 删除草稿版本资源（含对象清理）；已发布版本资源不可删除。 */
    void removeDraftResource(String skillId, String resourceId);
    /** 使用样例输入预览合成提示词，不调用模型。 */
    AgentSkillPreviewVo preview(String skillId, AgentSkillPreviewDto dto);
}
