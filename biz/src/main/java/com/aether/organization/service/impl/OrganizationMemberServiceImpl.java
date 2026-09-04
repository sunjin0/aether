package com.aether.organization.service.impl;
import com.aether.organization.entity.OrganizationMember; import com.aether.organization.mapper.OrganizationMemberMapper; import com.aether.organization.service.OrganizationMemberService; import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl; import org.springframework.stereotype.Service;
@Service public class OrganizationMemberServiceImpl extends ServiceImpl<OrganizationMemberMapper, OrganizationMember> implements OrganizationMemberService {}
