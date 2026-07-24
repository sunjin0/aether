package com.aether.knowledge.workflow;

import com.alibaba.fastjson2.JSONObject;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeReviewConfigResolver {

    public boolean isAiReviewRequired(String reviewConfig) {
        return booleanValue(reviewConfig, "aiReviewRequired", true);
    }

    public boolean isDifferentApproverRequired(String reviewConfig) {
        return booleanValue(reviewConfig, "requireDifferentApprover", true);
    }

    private boolean booleanValue(String value, String key, boolean defaultValue) {
        if (StringUtils.isBlank(value)) {
            throw new ServerException(500,
                    I18nUtils.getMessage("knowledge.review.configuration.required"));
        }
        try {
            Boolean configured = JSONObject.parseObject(value).getBoolean(key);
            return configured == null ? defaultValue : configured;
        } catch (Exception e) {
            throw new ServerException(500,
                    I18nUtils.getMessage("knowledge.review.configuration.invalid"));
        }
    }
}
