package com.aether.organization.service.impl;
import com.aether.organization.entity.Organization; import com.aether.organization.mapper.OrganizationMapper; import com.aether.organization.service.OrganizationService; import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl; import org.springframework.stereotype.Service;
@Service public class OrganizationServiceImpl extends ServiceImpl<OrganizationMapper, Organization> implements OrganizationService {}
