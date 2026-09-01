package com.aether.solution.service.impl;

import com.aether.solution.entity.Solution;
import com.aether.solution.mapper.SolutionMapper;
import com.aether.solution.service.SolutionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class SolutionServiceImpl extends ServiceImpl<SolutionMapper, Solution> implements SolutionService { }
