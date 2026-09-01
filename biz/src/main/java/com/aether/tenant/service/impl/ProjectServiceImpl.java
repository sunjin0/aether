package com.aether.tenant.service.impl;

import com.aether.tenant.entity.Project;
import com.aether.tenant.mapper.ProjectMapper;
import com.aether.tenant.service.ProjectService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService { }
