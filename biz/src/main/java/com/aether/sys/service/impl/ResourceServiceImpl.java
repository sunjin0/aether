package com.aether.sys.service.impl;


import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aether.sys.mapper.ResourceMapper;
import com.aether.sys.service.ResourceService;
import com.aether.entity.BaseEntity;
import com.aether.sys.entity.Resource;
import com.aether.enums.ResourceType;
import com.aether.i18n.I18nUtils;
import com.aether.sys.vo.ResourceVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 资源表 服务实现类
 * </p>
 *
 * @author sun
 * @since 2024-11-12
 */
@Service
public class ResourceServiceImpl extends ServiceImpl<ResourceMapper, Resource> implements ResourceService {

    /**
     * 查询当前请求。
     */
    @Override
    public Page<ResourceVo> list(ResourceVo resource) {
        // 获取国际化语言
        String lng = I18nUtils.getMessage("lng");
        // 分页
        Page<Resource> resourcePage = new Page<>(resource.getCurrent(), resource.getPageSize());
        // 查询条件
        Wrapper<Resource> queryWrapper = Wrappers.lambdaQuery(Resource.class)
                .like(StringUtils.isNotBlank(resource.getNameCn()), Resource::getNameCn, resource.getNameCn())
                .like(StringUtils.isNotBlank(resource.getName()), Resource::getName, resource.getName())
                .like(StringUtils.isNotBlank(resource.getPath()), Resource::getPath, resource.getPath())
                .like(StringUtils.isNotBlank(resource.getType()), Resource::getType, resource.getType())
                .like(StringUtils.isNotBlank(resource.getDescription()), Resource::getDescription, resource.getDescription())
                .eq(resource.getName() == null
                        && resource.getNameCn() == null
                        && resource.getPath() == null
                        && resource.getType() == null
                        && resource.getDescription() == null, Resource::getParentId, "0")
                .orderByAsc(Resource::getSortNum);
        // 查询
        Page<Resource> page = this.page(resourcePage, queryWrapper);
        List<ResourceVo> resourceVos = page.getRecords().stream().map(record -> {
            ResourceVo resourceVo = new ResourceVo();
            BeanUtils.copyProperties(record, resourceVo);
            resourceVo.setKey(record.getId());
            resourceVo.setTitle("en_US".equals(lng) ? record.getName() : record.getNameCn());
            return resourceVo;
        }).collect(Collectors.toList());
        if (resourceVos.isEmpty()) {
            return new Page<>();
        }
        // 获取父级id
        List<String> parentIds = resourceVos.stream().map(ResourceVo::getId).collect(Collectors.toList());
        // 查询非父id的所有子资源
        List<ResourceVo> list = this.list(Wrappers.lambdaQuery(Resource.class)
                .notIn(Resource::getId, parentIds)).stream().map(record -> {
            ResourceVo resourceVo = new ResourceVo();
            BeanUtils.copyProperties(record, resourceVo);
            resourceVo.setKey(record.getId());
            resourceVo.setTitle("en_US".equals(lng) ? record.getName() : record.getNameCn());
            return resourceVo;
        }).collect(Collectors.toList());
        resourceVos.addAll(list);
        // 处理父子级关系
        LinkedHashMap<String, ResourceVo> map = new LinkedHashMap<>();
        resourceVos.forEach(v -> {
            if (!map.containsKey(v.getId())) {
                map.put(v.getId(), v);
            }
        });
        Queue<ResourceVo> queue = new LinkedList<>(resourceVos);
        while (!queue.isEmpty()) {
            ResourceVo poll = queue.poll();
            ResourceVo parent = map.get(poll.getParentId().toString());
            if (parent != null) {
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(poll);
            }
        }

        Page<ResourceVo> voPage = new Page<>();
        voPage.setRecords(map.values().stream()
                .filter(vo -> Objects.equals(vo.getType(), ResourceType.ROUTE.getCode()))
                .filter(vo -> parentIds.contains(vo.getId()))
                .sorted(Comparator.comparing(BaseEntity::getSortNum))
                .collect(Collectors.toList()));
        voPage.setTotal(page.getTotal());
        return voPage;
    }

    /**
     * 保存当前请求。
     */
    @Override
    public boolean save(Resource resource, Boolean createNodes) {
        boolean save = super.save(resource);
        if (createNodes) {
            // 创建节点
            Resource readResource = new Resource();
            readResource.setName("Read");
            readResource.setNameCn("可读");
            readResource.setType(ResourceType.PERMISSION.getCode());
            readResource.setParentId(resource.getId());
            super.save(readResource);
            Resource writeResource = new Resource();
            writeResource.setName("Write");
            writeResource.setNameCn("可写");
            writeResource.setType(ResourceType.PERMISSION.getCode());
            writeResource.setParentId(resource.getId());
            super.save(writeResource);
        }
        return save;
    }
}
