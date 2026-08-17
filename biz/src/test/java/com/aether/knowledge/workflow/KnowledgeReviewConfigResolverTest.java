package com.aether.knowledge.workflow;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 验证知识库审核配置Resolver的行为。
 */
class KnowledgeReviewConfigResolverTest {
    private KnowledgeReviewConfigResolver resolver;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() {
        new I18nUtils(mock(I18nService.class));
        resolver = new KnowledgeReviewConfigResolver();
    }

    /**
     * 处理usesSecureDefaultsWhenKnownFlagsAreMissing。
     */
    @Test
    void usesSecureDefaultsWhenKnownFlagsAreMissing() {
        assertTrue(resolver.isAiReviewRequired("{}"));
        assertTrue(resolver.isDifferentApproverRequired("{}"));
    }

    /**
     * 处理readsExplicitConfigurationValues。
     */
    @Test
    void readsExplicitConfigurationValues() {
        String config = "{\"aiReviewRequired\":false,\"requireDifferentApprover\":false}";

        assertFalse(resolver.isAiReviewRequired(config));
        assertFalse(resolver.isDifferentApproverRequired(config));
    }

    /**
     * 处理rejectsMissingOrInvalidConfiguration。
     */
    @Test
    void rejectsMissingOrInvalidConfiguration() {
        assertThrows(ServerException.class, () -> resolver.isAiReviewRequired(null));
        assertThrows(ServerException.class, () -> resolver.isAiReviewRequired("{invalid"));
    }
}
