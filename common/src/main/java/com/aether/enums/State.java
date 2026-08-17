package com.aether.enums;

import lombok.Getter;


/**
 * 表示State。
 */
@Getter
public enum State {
    /**
     * 撤回
     */
    Revoke(3),

    /**
     * 失败
     */
    Fail(2),

    /**
     * 成功
     */
    Success(1),

    /**
     * 删除
     */
    Delete(0);

    private final int code;

    /**
     * 创建 {@code State} 实例。
     */
    State(int state) {
        this.code = state;
    }

}
