package com.aether.knowledge.workflow;

import com.alibaba.fastjson2.JSONObject;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 表示知识库审核配置Resolver。
 */
@Component
public class KnowledgeReviewConfigResolver {

    /**
     * 判断是否为Ai审核Required。
     */
    public boolean isAiReviewRequired(String reviewConfig) {
        return booleanValue(reviewConfig, "aiReviewRequired", true);
    }

    /**
     * 判断是否为DifferentApproverRequired。
     */
    public boolean isDifferentApproverRequired(String reviewConfig) {
        return booleanValue(reviewConfig, "requireDifferentApprover", true);
    }

    /**
     * Returns the administrator selected to review submitted documents.  An empty value keeps
     * the existing shared review-queue behaviour.
     */
    public String manualReviewerId(String reviewConfig) {
        if (StringUtils.isBlank(reviewConfig)) {
            throw new ServerException(500,
                    I18nUtils.getMessage("knowledge.review.configuration.required"));
        }
        try {
            return StringUtils.trimToNull(JSONObject.parseObject(reviewConfig)
                    .getString("manualReviewerId"));
        } catch (Exception e) {
            throw new ServerException(500,
                    I18nUtils.getMessage("knowledge.review.configuration.invalid"));
        }
    }

    /**
     * 处理booleanValue。
     */
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
