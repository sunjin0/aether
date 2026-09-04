package com.aether.organization.service.impl;
import com.aether.organization.entity.TeamMember; import com.aether.organization.mapper.TeamMemberMapper; import com.aether.organization.service.TeamMemberService; import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl; import org.springframework.stereotype.Service;
@Service public class TeamMemberServiceImpl extends ServiceImpl<TeamMemberMapper, TeamMember> implements TeamMemberService {}
