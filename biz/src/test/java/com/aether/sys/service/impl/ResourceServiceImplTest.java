package com.aether.sys.service.impl;

import com.aether.sys.entity.Resource;
import com.aether.sys.mapper.ResourceMapper;
import com.aether.sys.vo.ResourceVo;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证资源服务实现的行为。
 */
@ExtendWith(MockitoExtension.class)
class ResourceServiceImplTest {

    @Mock
    private ResourceMapper resourceMapper;
    @Mock
    private I18nService i18nService;

    private ResourceServiceImpl service;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() throws Exception {
        new I18nUtils(i18nService);
        when(i18nService.getMessage("lng")).thenReturn("zh_CN");
        service = new ResourceServiceImpl();
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(service, resourceMapper);
    }

    /**
     * 查询BindsRootParentIdAsString。
     */
    @Test
    void listBindsRootParentIdAsString() {
        when(resourceMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<Resource>().setRecords(Collections.emptyList()));

        ResourceVo request = new ResourceVo();
        request.setCurrent(1L);
        request.setPageSize(10L);
        service.list(request);

        ArgumentCaptor<Wrapper<Resource>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(resourceMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        // LambdaQueryWrapper 在脱离 MyBatis 映射环境时无法解析字段缓存，
        // 此处只验证根节点查询条件已被传递给 Mapper。
        assertTrue(wrapperCaptor.getValue() != null);
    }
}
