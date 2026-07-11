package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.mapper.AgentDefinitionMapper;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.entity.Option;
import com.aether.sys.entity.Dict;
import com.aether.sys.mapper.DictMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent定义 Service 实现
 */
@Service
public class AgentDefinitionServiceImpl extends ServiceImpl<AgentDefinitionMapper, AgentDefinition> implements AgentDefinitionService {
}
