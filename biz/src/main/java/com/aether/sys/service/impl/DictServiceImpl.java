package com.aether.sys.service.impl;

import com.aether.sys.mapper.DictMapper;
import com.aether.entity.Option;
import com.aether.sys.entity.Dict;
import com.aether.i18n.I18nUtils;
import com.aether.sys.vo.DictVo;
import com.aether.sys.service.DictService;
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
public class DictServiceImpl extends ServiceImpl<DictMapper, Dict> implements DictService {
    /**
     * 获取按Code。
     */
    @Override
    public Dict getByCode(String code, String lng) {
        Dict dist = this.getOne(Wrappers.lambdaQuery(Dict.class)
                .eq(Dict::getCode, code));
        if (dist != null) {
            SwitchLang(dist, lng);
            return dist;
        }
        return null;
    }

    /**
     * 处理SwitchLang。
     */
    private void SwitchLang(Dict dist, String lng) {
        lng = lng == null ? I18nUtils.getMessage("lng") : lng;
        switch (lng) {
            case "zh_CN":
                dist.setName(dist.getNameCn());
                break;
            case "en_US":
                dist.setName(dist.getName());
                break;
            default:
                break;
        }
    }

    /**
     * 查询当前请求。
     */
    @Override
    public Page<DictVo> list(DictVo dict) {
        //分页
        Page<Dict> page = new Page<>(dict.getCurrent(), dict.getPageSize());
        //查询
        LambdaQueryWrapper<Dict> query = Wrappers.lambdaQuery(Dict.class);
        query
                .like(StringUtils.isNotBlank(dict.getCode()), Dict::getCode, dict.getCode())
                .like(StringUtils.isNotBlank(dict.getNameCn()), Dict::getNameCn, dict.getNameCn())
                .like(StringUtils.isNotBlank(dict.getRemark()), Dict::getRemark, dict.getRemark())
                .like(StringUtils.isNotBlank(dict.getVal()), Dict::getVal, dict.getVal())
                .like(StringUtils.isNotBlank(dict.getName()), Dict::getName, dict.getName())
                .eq(StringUtils.isNotBlank(dict.getId()), Dict::getId, dict.getId())
                .orderByAsc(Dict::getSortNum);
        if (dict.getId() == null && dict.getName() == null && dict.getNameCn() == null && dict.getVal() == null && dict.getCode() == null && dict.getRemark() == null) {
            query.isNull(Dict::getParent);
        }
        Page<Dict> dictPage = page(page, query);
        List<Dict> records = dictPage.getRecords();
        if (records.isEmpty()) {
            return new Page<>();
        }
        //父code
        List<String> parentIds = records.stream().map(Dict::getCode).collect(Collectors.toList());
        //所有子资源
        List<Dict> list = list(Wrappers.lambdaQuery(Dict.class)
                .notIn(Dict::getCode, parentIds)
                .isNotNull(Dict::getParent));
        //添加所有子资源
        records.addAll(list);
        //所有资源
        LinkedHashMap<String, DictVo> dictList = new LinkedHashMap<>();
        //将父code作为key，资源作为value,放入map中并转化为vo
        List<DictVo> dictVoList = records.stream().map(item -> {
            DictVo vo = new DictVo();
            BeanUtils.copyProperties(item, vo);
            vo.setKey(item.getId());
            if (!dictList.containsKey(item.getCode())) {
                dictList.put(item.getCode(), vo);
            }
            return vo;
        }).collect(Collectors.toList());
        //构建树形结构
        for (DictVo item : dictVoList) {
            if (item.getParent() != null) {
                DictVo parent = dictList.get(item.getParent());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(item);
                }
            }
        }
        Page<DictVo> voPage = new Page<>();
        voPage.setRecords(dictList.values()
                .stream()
                .filter(item -> parentIds.contains(item.getCode()))
                .collect(Collectors.toList()));
        voPage.setTotal(dictPage.getTotal());
        return voPage;
    }

    /**
     * 处理info。
     */
    @Override
    public Dict info(String id) {
        return getById(id);
    }

    /**
     * 删除当前请求。
     */
    @Override
    public boolean delete(String id) {
        DictVo vo = new DictVo();
        vo.setId(id);
        ArrayList<String> ids = new ArrayList<>();
        vo.setCurrent(1L);
        vo.setPageSize(9999L);
        Page<DictVo> list = list(vo);
        if (list.getRecords().size() == 1) {
            Stack<DictVo> stack = new Stack<>();
            stack.push(list.getRecords().get(0));
            while (!stack.isEmpty()) {
                DictVo item = stack.pop();
                ids.add(item.getId());
                if (item.getChildren() != null) {
                    stack.addAll(item.getChildren());
                }
            }
        }
        return super.removeByIds(ids);
    }

    /**
     * 获取Options。
     */
    @Override
    public List<Option> getOptions(String parentCode, Boolean useValue) {
        List<Dict> list = getByParentCode(parentCode);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        list.forEach(Dict -> SwitchLang(Dict, null));
        return list.stream().map(item -> new Option(item.getName(), useValue ? item.getVal() : item.getCode())).collect(Collectors.toList());
    }

    /**
     * 处理select。
     */
    @Override
    public List<Dict> select() {
        return this.list(Wrappers.lambdaQuery(Dict.class)
                .orderByAsc(Dict::getSortNum));
    }

    /**
     * 获取Value。
     */
    @Override
    public String getValue(String code) {
        Dict dict = getByCode(code, null);
        return dict == null ? null : dict.getVal();
    }

    /**
     * 获取按ParentCode。
     */
    private List<Dict> getByParentCode(String parentCode) {
        return this.list(Wrappers.lambdaQuery(Dict.class)
                .eq(Dict::getParent, parentCode));
    }

}
