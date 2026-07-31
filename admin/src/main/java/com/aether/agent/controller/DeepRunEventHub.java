package com.aether.agent.controller;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/** In-memory fan-out for live Deep Run SSE subscribers. Durable replay is served from agent_run_step. */
final class DeepRunEventHub {
    private static final ConcurrentHashMap<String, Set<SseEmitter>> SUBSCRIBERS = new ConcurrentHashMap<>();

    private DeepRunEventHub() { }

    static void add(String runId, SseEmitter emitter) {
        SUBSCRIBERS.computeIfAbsent(runId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        Runnable remove = () -> remove(runId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
    }

    static void publish(String runId, String event, String data, boolean terminal) {
        Set<SseEmitter> emitters = SUBSCRIBERS.get(runId);
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

    private static void remove(String runId, SseEmitter emitter) {
        Set<SseEmitter> emitters = SUBSCRIBERS.get(runId);
        if (emitters == null) return;
        emitters.remove(emitter);
        if (emitters.isEmpty()) SUBSCRIBERS.remove(runId, emitters);
    }
}
