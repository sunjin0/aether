package com.aether.organization.service.impl;
import com.aether.organization.entity.Team; import com.aether.organization.mapper.TeamMapper; import com.aether.organization.service.TeamService; import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl; import org.springframework.stereotype.Service;
@Service public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team> implements TeamService {}
