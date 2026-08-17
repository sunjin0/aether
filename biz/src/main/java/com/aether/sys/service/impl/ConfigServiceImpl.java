package com.aether.sys.service.impl;

import com.aether.sys.mapper.ConfigMapper;
import com.aether.sys.entity.Config;
import com.aether.sys.vo.ConfigVo;
import com.aether.sys.service.ConfigService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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


    /**
     * 查询当前请求。
     */
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
        Page<Config> configPage = page(page, query);
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

    /**
     * 处理info。
     */
    @Override
    public Config info(String id) {
        return getById(id);
    }

    /**
     * 处理tree。
     */
    @Override
    public List<ConfigVo> tree() {
        return tree(new ConfigVo());
    }

    /**
     * 处理tree。
     */
    @Override
    public List<ConfigVo> tree(ConfigVo config) {
        List<Config> configs = list(Wrappers.lambdaQuery(Config.class)
                .orderByAsc(Config::getSortNum)
                .orderByAsc(Config::getCode));
        if (config != null && (StringUtils.isNotBlank(config.getName()) || StringUtils.isNotBlank(config.getCode())
                || StringUtils.isNotBlank(config.getValue()) || StringUtils.isNotBlank(config.getRemark()))) {
            Map<String, Config> byCode = configs.stream().collect(Collectors.toMap(Config::getCode, item -> item, (first, ignored) -> first));
            Set<String> includedCodes = configs.stream()
                    .filter(item -> containsIgnoreCase(item.getName(), config.getName())
                            && containsIgnoreCase(item.getCode(), config.getCode())
                            && containsIgnoreCase(item.getValue(), config.getValue())
                            && containsIgnoreCase(item.getRemark(), config.getRemark()))
                    .map(Config::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
            Deque<String> parents = new ArrayDeque<>(includedCodes);
            while (!parents.isEmpty()) {
                Config item = byCode.get(parents.pop());
                if (item != null && StringUtils.isNotBlank(item.getParent()) && includedCodes.add(item.getParent())) {
                    parents.push(item.getParent());
                }
            }
            configs = configs.stream().filter(item -> includedCodes.contains(item.getCode())).collect(Collectors.toList());
        }
        return buildTree(configs);
    }

    /**
     * 删除当前请求。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String id) {
        Config root = getById(id);
        if (root == null) {
            throw new ServerException(404, I18nUtils.getMessage("system.config.not-found"));
        }
        Map<String, List<Config>> childrenByParent = list(Wrappers.lambdaQuery(Config.class))
                .stream().collect(Collectors.groupingBy(Config::getParent));
        List<Config> toDelete = new ArrayList<>();
        Deque<Config> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Config current = stack.pop();
            toDelete.add(current);
            for (Config child : childrenByParent.getOrDefault(current.getCode(), Collections.<Config>emptyList())) {
                stack.push(child);
            }
        }
        toDelete.forEach(item -> item.setDeleted(true));
        return updateBatchById(toDelete);
    }

    /**
     * 创建当前请求。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean create(Config config) {
        validateForCreate(config);
        return save(config);
    }

    /**
     * 更新当前请求。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Config config) {
        if (config == null || StringUtils.isBlank(config.getId())) {
            throw new ServerException(400, I18nUtils.getMessage("system.config.id.required"));
        }
        Config existing = getById(config.getId());
        if (existing == null) {
            throw new ServerException(404, I18nUtils.getMessage("system.config.not-found"));
        }
        if (!StringUtils.equals(existing.getCode(), StringUtils.trimToEmpty(config.getCode()))) {
            throw new ServerException(409, I18nUtils.getMessage("system.config.code.immutable"));
        }
        validateFields(config);
        validateParent(config.getParent(), existing.getCode(), existing.getId());
        existing.setName(config.getName());
        existing.setParent(StringUtils.trimToNull(config.getParent()));
        existing.setValue(config.getValue());
        existing.setRemark(config.getRemark());
        existing.setSortNum(config.getSortNum());
        return updateById(existing);
    }

    /**
     * 获取Value。
     */
    @Override
    public String getValue(String code) {
        Config one = this.getOne(Wrappers.lambdaQuery(Config.class)
                .eq(Config::getCode, code)
                .orderByDesc(Config::getCreatedAt)
                .last("limit 1"));
        return one == null ? null : one.getValue();
    }

    /**
     * 校验用于创建。
     */
    private void validateForCreate(Config config) {
        validateFields(config);
        String code = StringUtils.trim(config.getCode());
        if (count(Wrappers.lambdaQuery(Config.class).eq(Config::getCode, code)) > 0) {
            throw new ServerException(409, I18nUtils.getMessage("system.config.code.exists"));
        }
        config.setCode(code);
        validateParent(config.getParent(), code, null);
    }

    /**
     * 校验Fields。
     */
    private void validateFields(Config config) {
        if (config == null || StringUtils.isBlank(config.getCode()) || StringUtils.isBlank(config.getName())
                || config.getValue() == null || config.getRemark() == null) {
            throw new ServerException(400, I18nUtils.getMessage("system.config.fields.required"));
        }
        if (config.getCode().length() > 255 || config.getName().length() > 255 || config.getValue().length() > 255 || config.getRemark().length() > 255) {
            throw new ServerException(400, I18nUtils.getMessage("system.config.fields.max-length"));
        }
    }

    /**
     * 校验Parent。
     */
    private void validateParent(String parent, String code, String id) {
        String parentCode = StringUtils.trimToNull(parent);
        if (parentCode == null) return;
        if (StringUtils.equals(parentCode, code)) {
            throw new ServerException(400, I18nUtils.getMessage("system.config.parent.self"));
        }
        Config parentConfig = getOne(Wrappers.lambdaQuery(Config.class).eq(Config::getCode, parentCode));
        if (parentConfig == null) {
            throw new ServerException(400, I18nUtils.getMessage("system.config.parent.not-found"));
        }
        if (id != null && isDescendant(parentCode, code)) {
            throw new ServerException(400, I18nUtils.getMessage("system.config.parent.descendant"));
        }
    }

    /**
     * 判断是否为Descendant。
     */
    private boolean isDescendant(String candidateCode, String ancestorCode) {
        Map<String, String> parentByCode = list(Wrappers.lambdaQuery(Config.class)).stream()
                .collect(Collectors.toMap(Config::getCode, Config::getParent, (first, ignored) -> first));
        String current = candidateCode;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (StringUtils.equals(current, ancestorCode)) return true;
            current = parentByCode.get(current);
        }
        return false;
    }

    /**
     * 构建Tree。
     */
    private List<ConfigVo> buildTree(List<Config> configs) {
        Map<String, ConfigVo> byCode = new LinkedHashMap<>();
        List<Config> sortedConfigs = new ArrayList<>(configs);
        sortedConfigs.sort(Comparator.comparing(Config::getSortNum, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Config::getCode, Comparator.nullsLast(String::compareTo)));
        for (Config item : sortedConfigs) {
            ConfigVo vo = new ConfigVo();
            BeanUtils.copyProperties(item, vo);
            vo.setKey(item.getId());
            vo.setChildren(new ArrayList<ConfigVo>());
            byCode.put(item.getCode(), vo);
        }
        List<ConfigVo> roots = new ArrayList<>();
        for (ConfigVo item : byCode.values()) {
            ConfigVo parent = StringUtils.isBlank(item.getParent()) ? null : byCode.get(item.getParent());
            if (parent == null) roots.add(item);
            else parent.getChildren().add(item);
        }
        return roots;
    }

    /**
     * 处理containsIgnoreCase。
     */
    private boolean containsIgnoreCase(String value, String query) {
        return StringUtils.isBlank(query) || StringUtils.containsIgnoreCase(value, query.trim());
    }
}
