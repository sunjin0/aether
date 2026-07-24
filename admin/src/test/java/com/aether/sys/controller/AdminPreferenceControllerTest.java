package com.aether.sys.controller;

import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.sys.entity.AdminPreference;
import com.aether.sys.service.AdminPreferenceEventService;
import com.aether.sys.service.AdminPreferenceService;
import com.aether.sys.vo.AdminPreferenceVo;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPreferenceControllerTest {

    @Mock
    private AdminPreferenceService preferenceService;
    @Mock
    private AdminPreferenceEventService eventService;
    @Mock
    private I18nService i18nService;

    private AdminPreferenceController controller;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AdminPreference.class);
        controller = new AdminPreferenceController(preferenceService, eventService);
        new I18nUtils(i18nService);
        HashMap<String, String> user = new HashMap<String, String>();
        user.put("userId", "user-1");
        CurrentUser.set(user);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.remove();
    }

    @Test
    void listIsAlwaysScopedToCurrentUser() {
        Page<AdminPreference> result = new Page<AdminPreference>();
        result.setRecords(Collections.<AdminPreference>emptyList());
        when(preferenceService.page(any(Page.class), any(Wrapper.class))).thenReturn(result);

        AdminPreferenceVo request = new AdminPreferenceVo();
        request.setAdminId("other-user");
        request.setCurrent(1L);
        request.setPageSize(20L);
        controller.list(request);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(preferenceService).page(any(Page.class), wrapperCaptor.capture());
        AbstractWrapper<?, ?, ?> wrapper =
                (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("admin_id"));
    }

    @Test
    void saveIgnoresSubmittedAdminIdAndCreatesExplicitPreference() {
        when(i18nService.getMessage(any(String.class))).thenReturn("ok");
        when(preferenceService.save(any(AdminPreference.class))).thenAnswer(invocation -> {
            AdminPreference preference = invocation.getArgument(0);
            preference.setId("preference-1");
            return true;
        });
        AdminPreferenceVo request = new AdminPreferenceVo();
        request.setAdminId("other-user");
        request.setCategory("style");
        request.setKeyName("response_style");
        request.setValue("concise");

        controller.save(request);

        ArgumentCaptor<AdminPreference> preferenceCaptor =
                ArgumentCaptor.forClass(AdminPreference.class);
        verify(preferenceService).save(preferenceCaptor.capture());
        assertEquals("user-1", preferenceCaptor.getValue().getAdminId());
        assertEquals(AdminPreference.SOURCE_EXPLICIT, preferenceCaptor.getValue().getSource());
        verify(preferenceService).updateEffectiveScore("preference-1");
    }
}
