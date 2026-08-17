package com.aether.agent.model;

import lombok.Data;

/**
 * Result of resolving a user's conversational query into a standalone query.
 */
@Data
public class QueryRewriteResult {

    private String rewrittenContent;
}
