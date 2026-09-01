package com.aether.tenant.service.impl;

import com.aether.tenant.entity.Tenant;
import com.aether.tenant.mapper.TenantMapper;
import com.aether.tenant.service.TenantService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class TenantServiceImpl extends ServiceImpl<TenantMapper, Tenant> implements TenantService { }
