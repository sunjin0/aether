package com.aether.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型上下文消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelChatMessage {

    private String role;

    private String content;
}
