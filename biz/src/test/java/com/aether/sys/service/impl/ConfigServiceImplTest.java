package com.aether.sys.service.impl;

import com.aether.sys.entity.Config;
import com.aether.sys.mapper.ConfigMapper;
import com.aether.sys.vo.ConfigVo;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 验证配置服务实现的行为。
 */
@ExtendWith(MockitoExtension.class)
class ConfigServiceImplTest {

    @Mock
    private ConfigMapper configMapper;

    private ConfigServiceImpl service;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() throws Exception {
        service = new ConfigServiceImpl();
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(service, configMapper);
    }

    /**
     * 处理treeBuildsSortedNestedConfigurationNodes。
     */
    @Test
    void treeBuildsSortedNestedConfigurationNodes() {
        Config root = config("root", null, "Root", 2);
        Config child = config("child", "root", "Child", 1);
        Config earlierRoot = config("alpha", null, "Alpha", 1);
        when(configMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(root, child, earlierRoot));

        List<ConfigVo> tree = service.tree();

        assertEquals(2, tree.size());
        assertEquals("alpha", tree.get(0).getCode());
        assertEquals("root", tree.get(1).getCode());
        assertEquals(1, tree.get(1).getChildren().size());
        assertEquals("child", tree.get(1).getChildren().get(0).getCode());
        assertTrue(tree.get(0).getChildren().isEmpty());
    }

    /**
     * 配置当前请求。
     */
    private Config config(String code, String parent, String name, int sortNum) {
        Config config = new Config();
        config.setId(code + "-id");
        config.setCode(code);
        config.setParent(parent);
        config.setName(name);
        config.setValue("value");
        config.setRemark("remark");
        config.setSortNum(sortNum);
        return config;
    }
}
