package com.aether.agent.skill.service.impl;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.mapper.AgentSkillVersionMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
/** Skill 版本基础持久化服务，由生命周期服务统一约束发布不可变性。 */
@Service public class AgentSkillVersionServiceImpl extends ServiceImpl<AgentSkillVersionMapper, AgentSkillVersion> { }
