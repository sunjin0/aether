package com.aether.openapi.dto;

import lombok.Data;

import java.util.Map;

/** A structured response to a pending OpenAPI Agent interaction. */
@Data
public class OpenApiAgentInteractionDto {
    private String idempotencyKey;
    private Map<String, Object> answer;
}
