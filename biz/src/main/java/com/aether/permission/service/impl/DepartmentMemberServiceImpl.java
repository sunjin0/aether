package com.aether.permission.service.impl;

import com.aether.permission.entity.DepartmentMember;
import com.aether.permission.mapper.DepartmentMemberMapper;
import com.aether.permission.service.DepartmentMemberService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class DepartmentMemberServiceImpl extends ServiceImpl<DepartmentMemberMapper, DepartmentMember>
        implements DepartmentMemberService {
}
