package com.aether.agent.skill.service;

import com.aether.agent.skill.dto.AgentSkillBindingUpdateDto;
import com.aether.agent.skill.dto.AgentSkillDraftDto;
import com.aether.agent.skill.dto.AgentSkillInstallDto;
import com.aether.agent.skill.dto.AgentSkillPreviewDto;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillResource;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.vo.*;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * Skill 草稿、发布和 Agent 安装生命周期服务。
 */
public interface AgentSkillService extends IService<AgentSkill> {
    /**
     * 创建 Skill 草稿并返回其编号。
     */
    String createDraft(AgentSkillDraftDto dto);

    /**
     * 基于当前 Skill 创建下一版可编辑草稿。
     */
    String createNextDraft(String skillId);

    /**
     * 更新指定 Skill 的可编辑草稿内容。
     */
    void updateDraft(String skillId, AgentSkillDraftDto dto);

    /**
     * 通过发布校验后发布 Skill 草稿，并生成不可变版本。
     */
    AgentSkillVersion publish(String skillId, String userId);

    /**
     * 查询 Skill 的草稿、已发布版本和资源详情。
     */
    AgentSkillDetailVo detail(String skillId);

    /**
     * 查询指定 Skill 的已发布版本列表。
     */
    List<AgentSkillVersion> listVersions(String skillId);

    /**
     * 查询指定智能体安装的 Skill 绑定列表。
     */
    List<AgentDefinitionSkillBinding> listBindings(String agentId);

    /**
     * 将指定 Skill 版本安装到智能体，并返回绑定编号。
     */
    String install(String agentId, AgentSkillInstallDto dto);

    /**
     * 更新智能体与 Skill 的绑定配置。
     */
    void updateBinding(String agentId, String bindingId, AgentSkillBindingUpdateDto dto);

    /**
     * 解除智能体与指定 Skill 的绑定。
     */
    void removeBinding(String agentId, String bindingId);

    /**
     * 上传资源到 Skill 草稿版本；content 为文件字节，fileName 用于类型判定。
     */
    AgentSkillResource uploadResource(String skillId, String fileName, String contentType, byte[] content, String purpose, String type);

    /**
     * Replaces an editable draft resource while retaining its resource ID.
     */
    AgentSkillResource updateDraftResource(String skillId, String resourceId, String fileName, String contentType, byte[] content, String purpose, String type);

    /**
     * 资源列表：有草稿时返回草稿资源，否则返回当前发布版本资源。
     */
    List<AgentSkillResource> listResources(String skillId);

    /**
     * 删除草稿版本资源（含对象清理）；已发布版本资源不可删除。
     */
    void removeDraftResource(String skillId, String resourceId);

    /**
     * 使用样例输入预览合成提示词，不调用模型。
     */
    AgentSkillPreviewVo preview(String skillId, AgentSkillPreviewDto dto);

    /**
     * 返回列表页所需的版本、绑定和依赖摘要。
     */
    AgentSkillVo lifecycle(AgentSkill skill);

    /**
     * 不修改数据的发布前检查；发布动作本身也会执行同一检查。
     */
    AgentSkillPublishCheckVo publishCheck(String skillId);

    /**
     * 汇总返回 Skill 的版本、安装、资源和依赖统计信息。
     */
    AgentSkillStatisticsVo statistics();
}
