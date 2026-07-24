package com.aether.sys.service.impl;

import com.aether.sys.entity.AdminPreference;
import com.aether.sys.service.AdminPreferenceEventService;
import com.aether.sys.mapper.AdminPreferenceMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPreferenceServiceImplTest {

    @Mock
    private AdminPreferenceMapper adminPreferenceMapper;

    @Mock
    private PreferenceReasoningEngine reasoningEngine;
    @Mock
    private AdminPreferenceEventService preferenceEventService;

    private AdminPreferenceServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new AdminPreferenceServiceImpl();
        java.lang.reflect.Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(service, adminPreferenceMapper);
        java.lang.reflect.Field reasoningEngineField = AdminPreferenceServiceImpl.class.getDeclaredField("reasoningEngine");
        reasoningEngineField.setAccessible(true);
        reasoningEngineField.set(service, reasoningEngine);
        java.lang.reflect.Field eventServiceField =
                AdminPreferenceServiceImpl.class.getDeclaredField("preferenceEventService");
        eventServiceField.setAccessible(true);
        eventServiceField.set(service, preferenceEventService);
    }

    @Test
    void buildPreferenceContext_delegatesToReasoningEngine() {
        String adminId = "admin1";
        String taskType = "code_review";
        String expectedContext = "【User Preferences】\n- [style] Prefer concise code";

        when(reasoningEngine.buildPreferenceContext(adminId, taskType)).thenReturn(expectedContext);

        String result = service.buildPreferenceContext(adminId, taskType);

        assertEquals(expectedContext, result);
        verify(reasoningEngine).buildPreferenceContext(adminId, taskType);
    }

    @Test
    void getEffectivePreference_filtersByKey() {
        String adminId = "admin1";
        String keyName = "code_style";
        String taskType = "code_review";

        AdminPreference pref1 = new AdminPreference();
        pref1.setKeyName("code_style");
        pref1.setValue("concise");

        AdminPreference pref2 = new AdminPreference();
        pref2.setKeyName("language");
        pref2.setValue("java");

        List<AdminPreference> effectivePrefs = Arrays.asList(pref1, pref2);
        when(reasoningEngine.resolveEffectivePreferences(adminId, taskType)).thenReturn(effectivePrefs);

        AdminPreference result = service.getEffectivePreference(adminId, keyName, taskType);

        assertNotNull(result);
        assertEquals("code_style", result.getKeyName());
        assertEquals("concise", result.getValue());
    }

    @Test
    void getEffectivePreference_returnsNullWhenKeyNotFound() {
        String adminId = "admin1";
        String keyName = "nonexistent";
        String taskType = "code_review";

        AdminPreference pref1 = new AdminPreference();
        pref1.setKeyName("code_style");

        List<AdminPreference> effectivePrefs = Collections.singletonList(pref1);
        when(reasoningEngine.resolveEffectivePreferences(adminId, taskType)).thenReturn(effectivePrefs);

        AdminPreference result = service.getEffectivePreference(adminId, keyName, taskType);

        assertNull(result);
    }

    @Test
    void incrementUsage_updatesCountAndLastUsedAt() {
        String preferenceId = "pref1";
        AdminPreference pref = new AdminPreference();
        pref.setId(preferenceId);
        pref.setUsageCount(5);
        pref.setConfidence(new BigDecimal("0.80"));
        pref.setPriority(50);
        pref.setDecayRate(BigDecimal.ZERO);
        pref.setAdminId("admin1");

        when(adminPreferenceMapper.selectById(preferenceId)).thenReturn(pref);
        when(adminPreferenceMapper.updateById(any(AdminPreference.class))).thenReturn(1);

        service.incrementUsage(preferenceId);

        verify(adminPreferenceMapper, atLeast(2)).updateById(any(AdminPreference.class));
        verify(reasoningEngine).clearUserCache("admin1");
    }

    @Test
    void incrementUsage_doesNothingWhenPreferenceNotFound() {
        String preferenceId = "nonexistent";
        when(adminPreferenceMapper.selectById(preferenceId)).thenReturn(null);

        service.incrementUsage(preferenceId);

        verify(adminPreferenceMapper, never()).updateById(any());
        verify(reasoningEngine, never()).clearUserCache(anyString());
    }

    @Test
    void adjustConfidence_clampsBetweenZeroAndOne() {
        String preferenceId = "pref1";
        AdminPreference pref = new AdminPreference();
        pref.setId(preferenceId);
        pref.setConfidence(new BigDecimal("0.8"));
        pref.setAdminId("admin1");

        when(adminPreferenceMapper.selectById(preferenceId)).thenReturn(pref);
        when(adminPreferenceMapper.updateById(any(AdminPreference.class))).thenReturn(1);

        service.adjustConfidence(preferenceId, new BigDecimal("0.3"));

        verify(adminPreferenceMapper).updateById(argThat(updated ->
            updated.getConfidence().compareTo(new BigDecimal("1.0")) == 0
        ));
        verify(reasoningEngine).clearUserCache("admin1");
    }

    @Test
    void adjustConfidence_disablesWhenBelowThreshold() {
        String preferenceId = "pref1";
        AdminPreference pref = new AdminPreference();
        pref.setId(preferenceId);
        pref.setConfidence(new BigDecimal("0.4"));
        pref.setAdminId("admin1");

        when(adminPreferenceMapper.selectById(preferenceId)).thenReturn(pref);
        when(adminPreferenceMapper.updateById(any(AdminPreference.class))).thenReturn(1);

        service.adjustConfidence(preferenceId, new BigDecimal("-0.2"));

        verify(adminPreferenceMapper).updateById(argThat(updated ->
            updated.getConfidence().compareTo(new BigDecimal("0.2")) == 0 &&
            updated.getStatus() == AdminPreference.STATUS_DISABLED
        ));
        verify(reasoningEngine).clearUserCache("admin1");
    }

    @Test
    void updateEffectiveScore_delegatesToMapper() {
        String preferenceId = "pref1";
        AdminPreference pref = new AdminPreference();
        pref.setId(preferenceId);
        pref.setAdminId("admin1");
        pref.setPriority(50);
        pref.setConfidence(new BigDecimal("0.80"));
        pref.setUsageCount(3);
        pref.setDecayRate(new BigDecimal("0.01"));
        pref.setLastUsedAt(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 5);

        when(adminPreferenceMapper.selectById(preferenceId)).thenReturn(pref);
        when(adminPreferenceMapper.updateById(any(AdminPreference.class))).thenReturn(1);

        service.updateEffectiveScore(preferenceId);

        verify(adminPreferenceMapper).updateById(argThat(updated ->
            updated.getEffectiveScore() != null &&
            updated.getEffectiveScore().compareTo(BigDecimal.ZERO) > 0
        ));
        verify(reasoningEngine).clearUserCache("admin1");
    }

    @Test
    void listByAdminId_returnsSortedPreferences() {
        String adminId = "admin1";
        AdminPreference pref1 = new AdminPreference();
        pref1.setAdminId(adminId);
        pref1.setEffectiveScore(new BigDecimal("80.00"));

        AdminPreference pref2 = new AdminPreference();
        pref2.setAdminId(adminId);
        pref2.setEffectiveScore(new BigDecimal("90.00"));

        when(adminPreferenceMapper.selectList(any(Wrapper.class)))
                .thenReturn(Arrays.asList(pref2, pref1));

        List<AdminPreference> result = service.listByAdminId(adminId);

        assertEquals(2, result.size());
        verify(adminPreferenceMapper).selectList(any(Wrapper.class));
    }

    @Test
    void clearUserCache_delegatesToReasoningEngine() {
        String adminId = "admin1";

        boolean result = service.clearUserCache(adminId);

        assertTrue(result);
        verify(reasoningEngine).clearUserCache(adminId);
    }

    @Test
    void evidenceRemovalDisablesOrphanedImplicitPreference() {
        AdminPreference preference = new AdminPreference();
        preference.setId("pref1");
        preference.setAdminId("admin1");
        preference.setSource(AdminPreference.SOURCE_IMPLICIT);
        preference.setStatus(AdminPreference.STATUS_ENABLED);
        preference.setConfidence(new BigDecimal("0.80"));
        preference.setEffectiveScore(new BigDecimal("40"));
        when(adminPreferenceMapper.selectById("pref1")).thenReturn(preference);
        when(preferenceEventService.count(any(Wrapper.class))).thenReturn(0L);
        when(adminPreferenceMapper.updateById(any(AdminPreference.class))).thenReturn(1);

        service.reconcileAfterEvidenceRemoval(Collections.singletonList("pref1"));

        verify(adminPreferenceMapper).updateById(argThat(updated ->
                updated.getStatus() == AdminPreference.STATUS_DISABLED
                        && BigDecimal.ZERO.compareTo(updated.getConfidence()) == 0
                        && BigDecimal.ZERO.compareTo(updated.getEffectiveScore()) == 0));
    }

    @Test
    void evidenceRemovalKeepsImplicitPreferenceWithOtherEvidence() {
        AdminPreference preference = new AdminPreference();
        preference.setId("pref1");
        preference.setSource(AdminPreference.SOURCE_IMPLICIT);
        when(adminPreferenceMapper.selectById("pref1")).thenReturn(preference);
        when(preferenceEventService.count(any(Wrapper.class))).thenReturn(1L, 0L);

        service.reconcileAfterEvidenceRemoval(Collections.singletonList("pref1"));

        verify(adminPreferenceMapper, never()).updateById(any(AdminPreference.class));
    }

    @Test
    void evidenceRemovalNeverDisablesExplicitPreference() {
        AdminPreference preference = new AdminPreference();
        preference.setId("pref1");
        preference.setSource(AdminPreference.SOURCE_EXPLICIT);
        when(adminPreferenceMapper.selectById("pref1")).thenReturn(preference);

        service.reconcileAfterEvidenceRemoval(Collections.singletonList("pref1"));

        verify(preferenceEventService, never()).count(any(Wrapper.class));
        verify(adminPreferenceMapper, never()).updateById(any(AdminPreference.class));
    }
}
