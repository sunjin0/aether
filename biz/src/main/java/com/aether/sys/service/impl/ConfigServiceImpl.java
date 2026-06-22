package com.aether.sys.service.impl;

import com.aether.sys.mapper.ConfigMapper;
import com.aether.sys.entity.Config;
import com.aether.sys.vo.ConfigVo;
import com.aether.sys.service.ConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 系统字典表 服务实现类
 * </p>
 *
 * @author sun
 * @since 2024-11-27
 */
@Service
public class ConfigServiceImpl extends ServiceImpl<ConfigMapper, Config> implements ConfigService {


    @Override
    public Page<ConfigVo> list(ConfigVo config) {
        //分页
        Page<Config> page = new Page<>(config.getCurrent(), config.getPageSize());
        //查询
        LambdaQueryWrapper<Config> query = Wrappers.lambdaQuery(Config.class);
        query
                .like(StringUtils.isNotBlank(config.getCode()), Config::getCode, config.getCode())
                .like(StringUtils.isNotBlank(config.getRemark()), Config::getRemark, config.getRemark())
                .like(StringUtils.isNotBlank(config.getValue()), Config::getValue, config.getValue())
                .like(StringUtils.isNotBlank(config.getName()), Config::getName, config.getName())
                .eq(StringUtils.isNotBlank(config.getId()), Config::getId, config.getId())
                .orderByAsc(Config::getSortNum);
        if (config.getId() == null && config.getName() == null && config.getValue() == null && config.getCode() == null && config.getRemark() == null) {
            query.isNull(Config::getParent);
        }
        Page<Config>configPage = page(page, query);
        List<Config> records = configPage.getRecords();
        if (records.isEmpty()) {
            return new Page<>();
        }
        //父code
        List<String> parentIds = records.stream().map(Config::getCode).collect(Collectors.toList());
        //所有子资源
        List<Config> list = list(Wrappers.lambdaQuery(Config.class)
                .notIn(Config::getCode, parentIds)
                .isNotNull(Config::getParent));
        //添加所有子资源
        records.addAll(list);
        //所有资源
        LinkedHashMap<String, ConfigVo> configList = new LinkedHashMap<>();
        //将父code作为key，资源作为value,放入map中并转化为vo
        List<ConfigVo> configVoList = records.stream().map(item -> {
            ConfigVo vo = new ConfigVo();
            BeanUtils.copyProperties(item, vo);
            vo.setKey(item.getId());
            if (!configList.containsKey(item.getCode())) {
                configList.put(item.getCode(), vo);
            }
            return vo;
        }).collect(Collectors.toList());
        //构建树形结构
        for (ConfigVo item : configVoList) {
            if (item.getParent() != null) {
                ConfigVo parent = configList.get(item.getParent());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(item);
                }
            }
        }
        Page<ConfigVo> voPage = new Page<>();
        voPage.setRecords(configList.values()
                .stream()
                .filter(item -> parentIds.contains(item.getCode()))
                .collect(Collectors.toList()));
        voPage.setTotal(configPage.getTotal());
        return voPage;
    }
    @Override
    public Config info(String id) {
        return getById(id);
    }

    @Override
    public boolean delete(String id) {
        ConfigVo vo = new ConfigVo();
        vo.setId(id);
        ArrayList<String> ids = new ArrayList<>();
        Page<ConfigVo> list = list(vo);
        if (list.getRecords().size() == 1) {
            Stack<ConfigVo> stack = new Stack<>();
            stack.push(list.getRecords().get(0));
            while (!stack.isEmpty()) {
                ConfigVo item = stack.pop();
                ids.add(item.getId());
                if (item.getChildren() != null) {
                    stack.addAll(item.getChildren());
                }
            }
        }
        return update(Wrappers.lambdaUpdate(Config.class)
                .in(Config::getId, ids));
    }
    @Override
    public String getValue(String code) {
        Config one = this.getOne(Wrappers.lambdaQuery(Config.class)
                .eq(Config::getCode, code)
                .orderByDesc(Config::getCreatedAt)
                .last("limit 1"));
        return one == null ? null : one.getValue();
    }
}
