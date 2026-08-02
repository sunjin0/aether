package com.aether.workflow.runtime;

import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 工作流运行事件 SSE 广播器；事件是实时视图，完整审计仍以节点实例表为准。 */
@Component
public class WorkflowSseHub {
    private final ConcurrentHashMap<String, List<SseEmitter>> emitters = new ConcurrentHashMap<String, List<SseEmitter>>();
    public SseEmitter subscribe(String instanceId) {
        final SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(instanceId, key -> new CopyOnWriteArrayList<SseEmitter>()).add(emitter);
        emitter.onCompletion(() -> remove(instanceId, emitter)); emitter.onTimeout(() -> remove(instanceId, emitter));
        return emitter;
    }
    public void publish(String instanceId, String event, Object payload) {
        List<SseEmitter> list = emitters.get(instanceId); if (list == null) return;
        for (SseEmitter emitter : list) try { emitter.send(SseEmitter.event().name(event).data(JSON.toJSONString(payload))); }
        catch (IOException e) { remove(instanceId, emitter); }
    }
    private void remove(String id, SseEmitter emitter) { List<SseEmitter> list = emitters.get(id); if (list != null) { list.remove(emitter); if (list.isEmpty()) emitters.remove(id); } }
}
