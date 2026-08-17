package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.local.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 验证知识库Access服务实现的行为。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeAccessServiceImplTest {
    @Mock
    private KnowledgeBaseService baseService;
    private KnowledgeAccessServiceImpl service;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() {
        service = new KnowledgeAccessServiceImpl(baseService);
        HashMap<String, String> user = new HashMap<>();
        user.put("userId", "admin-1");
        CurrentUser.set(user);
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
        platform.setVisibility("platform");
        KnowledgeBase owned = new KnowledgeBase();
        owned.setId("kb-2");
        owned.setOwnerAdminId("admin-1");
        when(baseService.list(any())).thenReturn(Arrays.asList(platform, owned));
        assertEquals(Arrays.asList("kb-1", "kb-2"), service.readableKnowledgeBaseIds());
    }

    /**
     * 处理private知识库Base判断是否为ReadableWhenAccessControl判断是否为Disabled。
     */
    @Test
    void privateKnowledgeBaseIsReadableWhenAccessControlIsDisabled() {
        KnowledgeBase base = new KnowledgeBase().setVisibility("private");
        base.setId("kb-1");
        base.setOwnerAdminId("admin-2");
        when(baseService.getById("kb-1")).thenReturn(base);
        assertSame(base, service.requireReadable("kb-1"));
    }

    /**
     * 处理anyAuthenticatedAdministratorMayWriteWhenAccessControl判断是否为Disabled。
     */
    @Test
    void anyAuthenticatedAdministratorMayWriteWhenAccessControlIsDisabled() {
        KnowledgeBase base = new KnowledgeBase().setVisibility("shared");
        base.setId("kb-1");
        base.setOwnerAdminId("admin-2");
        when(baseService.getById("kb-1")).thenReturn(base);

        assertSame(base, service.requireWritable("kb-1"));
    }
}
