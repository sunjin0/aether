package com.aether.agent.model;

/** 模型、检索和工具适配器共享的协作式取消信号。 */
public interface CancellationToken {
    boolean isCancelled();

    /** 在已取消时抛出取消异常，阻止后续耗时操作。 */
    default void throwIfCancelled() {
        if (isCancelled()) throw new java.util.concurrent.CancellationException("Agent 运行已取消");
    }
}
