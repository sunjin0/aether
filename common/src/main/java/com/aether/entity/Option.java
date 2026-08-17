package com.aether.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示Option。
 */
@Data
@NoArgsConstructor
public class Option {
    private String label;
    private Object value;
    private String code;
    private Integer status;

    /**
     * 创建 {@code Option} 实例。
     */
    public Option(String label, Object value) {
        this.label = label;
        this.value = value;
    }
}
