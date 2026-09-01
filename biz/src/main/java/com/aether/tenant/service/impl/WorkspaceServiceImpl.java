package com.aether.tenant.service.impl;

import com.aether.tenant.entity.Workspace;
import com.aether.tenant.mapper.WorkspaceMapper;
import com.aether.tenant.service.WorkspaceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceServiceImpl extends ServiceImpl<WorkspaceMapper, Workspace> implements WorkspaceService { }
