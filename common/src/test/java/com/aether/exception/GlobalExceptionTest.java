package com.aether.exception;

import com.aether.entity.WebResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GlobalExceptionTest {
    @BeforeEach
    void initI18n() {
        I18nService service = mock(I18nService.class);
        when(service.getMessage("error.internal")).thenReturn("系统内部错误");
        ReflectionTestUtils.setField(I18nUtils.class, "i18nService", service);
    }

    @AfterEach
    void clearI18n() { ReflectionTestUtils.setField(I18nUtils.class, "i18nService", null); }

    @Test
    void unknownExceptionMessageIsNotReturned() {
        ResponseEntity<WebResponse<String>> response = new GlobalException()
                .handleOtherException(new RuntimeException("jdbc password=secret"));

        assertEquals(500, response.getStatusCodeValue());
        assertNotEquals("jdbc password=secret", response.getBody().getMessage());
    }

    @Test
    void businessExceptionMasksCredentialValue() {
        ResponseEntity<WebResponse<String>> response = new GlobalException()
                .handleServerException(new ServerException(502, "downstream token=secret-value"));

        assertEquals(502, response.getStatusCodeValue());
        assertEquals("downstream token=[REDACTED]", response.getBody().getMessage());
    }
}
