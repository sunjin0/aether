package com.aether.agent.product.service.impl;
import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.agent.product.mapper.AgentProductProfileMapper;
import com.aether.agent.product.service.AgentProductProfileService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
@Service public class AgentProductProfileServiceImpl extends ServiceImpl<AgentProductProfileMapper, AgentProductProfile> implements AgentProductProfileService { }
