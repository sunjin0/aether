package com.aether.agent.service;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.service.impl.KnowledgeDocumentContentExtractor;
import com.aether.local.CurrentUser;
import com.aether.storage.service.ObjectStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预览接口把客户端传回来的 objectKey 直接当读取键，因此归属校验是它的全部边界。
 */
class ChatAttachmentServiceTest {
    private static final String BUCKET = "aether-chat";

    private ObjectStorageService storage;
    private ChatAttachmentService service;

    @BeforeEach
    void setUp() {
        I18nService i18n = mock(I18nService.class);
        when(i18n.getMessage(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(I18nUtils.class, "i18nService", i18n);
        storage = mock(ObjectStorageService.class);
        service = new ChatAttachmentService(mock(KnowledgeDocumentContentExtractor.class), storage, BUCKET, 10485760L, 100000);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.remove();
        ReflectionTestUtils.setField(I18nUtils.class, "i18nService", null);
    }

    private static void signInAsTenant(String tenantId) {
        HashMap<String, String> user = new HashMap<String, String>();
        if (tenantId != null) user.put("tenantId", tenantId);
        CurrentUser.set(user);
    }

    @Test
    void theCallersOwnAttachmentIsReadFromItsTenantPrefix() {
        signInAsTenant("tenant-1");
        byte[] bytes = new byte[]{1, 2, 3};
        when(storage.getObject(BUCKET, "chat/tenant-1/2026/09/24/abc.png")).thenReturn(bytes);

        assertArrayEquals(bytes, service.readOwnedAttachment("chat/tenant-1/2026/09/24/abc.png"));
    }

    /** objectKey 落在别的前缀下就是「读别人家对象」的尝试，一律当作不存在。 */
    @Test
    void aKeyOutsideTheTenantPrefixIsRejectedAsNotFound() {
        signInAsTenant("tenant-1");

        for (String key : new String[]{
                "chat/tenant-2/2026/09/24/abc.png",
                "knowledge/tenant-1/doc.pdf",
                "../chat/tenant-1/2026/09/24/abc.png",
                "chat2/tenant-1/2026/09/24/abc.png"}) {
            ServerException error = assertThrows(ServerException.class, () -> service.readOwnedAttachment(key), key);
            assertTrue(error.getMessage().startsWith("404:"), key);
        }
        verify(storage, never()).getObject(anyString(), anyString());
    }

    /** 前缀校验挡不住上跳：这个键是以 "chat/tenant-1/" 开头的，但读的是别的租户。 */
    @Test
    void aKeyThatEscapesItsTenantThroughDotDotIsRejected() {
        signInAsTenant("tenant-1");

        for (String key : new String[]{
                "chat/tenant-1/../tenant-2/2026/09/24/abc.png",
                "chat/tenant-1/./../../tenant-2/abc.png"}) {
            ServerException error = assertThrows(ServerException.class, () -> service.readOwnedAttachment(key), key);
            assertTrue(error.getMessage().startsWith("404:"), key);
        }
        verify(storage, never()).getObject(anyString(), anyString());
    }

    @Test
    void aBlankKeyIsRejectedBeforeTouchingStorage() {
        signInAsTenant("tenant-1");

        ServerException error = assertThrows(ServerException.class, () -> service.readOwnedAttachment("  "));

        assertTrue(error.getMessage().startsWith("422:"));
        verify(storage, never()).getObject(anyString(), anyString());
    }
}
