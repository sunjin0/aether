package com.aether.permission.service.impl;

import com.aether.permission.entity.Department;
import com.aether.permission.mapper.DepartmentMapper;
import com.aether.permission.service.DepartmentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department>
        implements DepartmentService {
}
