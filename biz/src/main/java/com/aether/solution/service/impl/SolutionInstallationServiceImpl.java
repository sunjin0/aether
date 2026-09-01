package com.aether.solution.service.impl;

import com.aether.solution.entity.SolutionInstallation;
import com.aether.solution.mapper.SolutionInstallationMapper;
import com.aether.solution.service.SolutionInstallationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class SolutionInstallationServiceImpl extends ServiceImpl<SolutionInstallationMapper, SolutionInstallation>
        implements SolutionInstallationService { }
