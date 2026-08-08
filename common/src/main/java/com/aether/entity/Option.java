package com.aether.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Option {
    private String label;
    private Object value;
    private String code;
    private Integer status;

    public Option(String label, Object value) {
        this.label = label;
        this.value = value;
    }
}
