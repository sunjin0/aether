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

class KnowledgeReviewConfigResolverTest {
    private KnowledgeReviewConfigResolver resolver;

    @BeforeEach
    void setUp() {
        new I18nUtils(mock(I18nService.class));
        resolver = new KnowledgeReviewConfigResolver();
    }

    @Test
    void usesSecureDefaultsWhenKnownFlagsAreMissing() {
        assertTrue(resolver.isAiReviewRequired("{}"));
        assertTrue(resolver.isDifferentApproverRequired("{}"));
    }

    @Test
    void readsExplicitConfigurationValues() {
        String config = "{\"aiReviewRequired\":false,\"requireDifferentApprover\":false}";

        assertFalse(resolver.isAiReviewRequired(config));
        assertFalse(resolver.isDifferentApproverRequired(config));
    }

    @Test
    void rejectsMissingOrInvalidConfiguration() {
        assertThrows(ServerException.class, () -> resolver.isAiReviewRequired(null));
        assertThrows(ServerException.class, () -> resolver.isAiReviewRequired("{invalid"));
    }
}
