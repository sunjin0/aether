package com.aether.sys.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.mapper.ServiceAccountMapper;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证服务账号产品授权的调用行为。
 */
@ExtendWith(MockitoExtension.class)
class ServiceAccountServiceImplTest {
    @Mock
    private ServiceAccountMapper serviceAccountMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private AgentDefinitionService agentDefinitionService;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private ServiceAccountServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        I18nService i18n = mock(I18nService.class);
        lenient().when(i18n.getMessage(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(I18nUtils.class, "i18nService", i18n);
        service = new ServiceAccountServiceImpl(new BCryptPasswordEncoder(), redisTemplate, agentDefinitionService, 900);
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(service, serviceAccountMapper);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(I18nUtils.class, "i18nService", null);
    }

    @Test
    void assertAgentCallAllowedRejectsEmptyProductScope() {
        ServiceAccount account = account("sa-1", 0);
        when(serviceAccountMapper.selectById("sa-1")).thenReturn(account);

        assertThrows(ServerException.class, () -> service.assertAgentCallAllowed("sa-1", "agent-1"));
    }

    @Test
    private ServiceAccount account(String id, int hourlyLimit) {
        ServiceAccount account = new ServiceAccount();
        account.setId(id);
        account.setEnabled(true);
        account.setDeleted(false);
        account.setAllowedProductIds("[]");
        account.setMaxAgentCallsPerHour(hourlyLimit);
        return account;
    }
}
