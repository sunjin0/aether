package com.aether.agent.runtime;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * In-memory fan-out for live Deep Run SSE subscribers. Durable replay is served from agent_run_step.
 */
@Component
public class DeepRunEventHub {
    private final ConcurrentHashMap<String, Set<SseEmitter>> subscribers = new ConcurrentHashMap<String, Set<SseEmitter>>();

    /**
     * 新增当前请求。
     */
    public void add(String runId, SseEmitter emitter) {
        subscribers.computeIfAbsent(runId, ignored -> new CopyOnWriteArraySet<SseEmitter>()).add(emitter);
        Runnable remove = () -> remove(runId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
    }

    /**
     * 发布当前请求。
     */
    public void publish(String runId, String event, String data, boolean terminal) {
        Set<SseEmitter> emitters = subscribers.get(runId);
        if (emitters == null) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
                if (terminal) emitter.complete();
            } catch (IOException | IllegalStateException ignored) {
                emitter.completeWithError(ignored);
            } finally {
                if (terminal) remove(runId, emitter);
            }
        }
    }

    /**
     * 移除当前请求。
     */
    private void remove(String runId, SseEmitter emitter) {
        Set<SseEmitter> emitters = subscribers.get(runId);
        if (emitters == null) return;
        emitters.remove(emitter);
        if (emitters.isEmpty()) subscribers.remove(runId, emitters);
    }
}
