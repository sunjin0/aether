package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.local.CurrentUser;
import com.aether.sys.service.AccountDataScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 验证知识库Access服务实现的行为。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeAccessServiceImplTest {
    @Mock
    private KnowledgeBaseService baseService;
    @Mock
    private AccountDataScopeService dataScopeService;
    private KnowledgeAccessServiceImpl service;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() {
        service = new KnowledgeAccessServiceImpl(baseService, dataScopeService);
        HashMap<String, String> user = new HashMap<>();
        user.put("userId", "admin-1");
        CurrentUser.set(user);
        lenient().when(dataScopeService.readableCreatorIds(any())).thenReturn(Collections.singletonList("admin-1"));
    }

    /**
     * 处理tearDown。
     */
    @AfterEach
    void tearDown() {
        CurrentUser.remove();
    }

    /**
     * 处理readableIdsInclude全部知识库BasesWhenAccessControl判断是否为Disabled。
     */
    @Test
    void readableIdsIncludeAllKnowledgeBasesWhenAccessControlIsDisabled() {
        KnowledgeBase platform = new KnowledgeBase();
        platform.setId("kb-1");
        platform.setCreatedBy("admin-1");
        KnowledgeBase owned = new KnowledgeBase();
        owned.setId("kb-2");
        owned.setCreatedBy("admin-1");
        when(baseService.list(any())).thenReturn(Arrays.asList(platform, owned));
        assertEquals(Arrays.asList("kb-1", "kb-2"), service.readableKnowledgeBaseIds());
    }

    /**
     * 处理private知识库Base判断是否为ReadableWhenAccessControl判断是否为Disabled。
     */
    @Test
    void privateKnowledgeBaseIsReadableWhenAccessControlIsDisabled() {
        KnowledgeBase base = new KnowledgeBase();
        base.setId("kb-1");
        base.setCreatedBy("admin-1");
        when(baseService.getById("kb-1")).thenReturn(base);
        assertSame(base, service.requireReadable("kb-1"));
    }

    /**
     * 处理anyAuthenticatedAdministratorMayWriteWhenAccessControl判断是否为Disabled。
     */
    @Test
    void anyAuthenticatedAdministratorMayWriteWhenAccessControlIsDisabled() {
        KnowledgeBase base = new KnowledgeBase();
        base.setId("kb-1");
        base.setCreatedBy("admin-1");
        when(baseService.getById("kb-1")).thenReturn(base);

        assertSame(base, service.requireWritable("kb-1"));
    }
}
