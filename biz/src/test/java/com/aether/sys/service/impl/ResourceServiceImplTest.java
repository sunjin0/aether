package com.aether.sys.service.impl;

import com.aether.sys.entity.Resource;
import com.aether.sys.mapper.ResourceMapper;
import com.aether.sys.vo.ResourceVo;
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

@ExtendWith(MockitoExtension.class)
class ResourceServiceImplTest {

    @Mock
    private ResourceMapper resourceMapper;

    private ResourceServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ResourceServiceImpl();
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(service, resourceMapper);
    }

    @Test
    void listBindsRootParentIdAsString() {
        when(resourceMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<Resource>().setRecords(Collections.emptyList()));

        service.list(new ResourceVo());

        ArgumentCaptor<Wrapper<Resource>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(resourceMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue("0"));
    }
}
