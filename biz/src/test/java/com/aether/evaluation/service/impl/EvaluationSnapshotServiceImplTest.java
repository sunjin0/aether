package com.aether.evaluation.service.impl;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluationSnapshotServiceImplTest {
    @Test void canonicalJsonSortsObjectKeysWithoutChangingArrayOrder() throws Exception {
        EvaluationSnapshotServiceImpl service = new EvaluationSnapshotServiceImpl(null, null, null, null, null, null);
        Method canonical = EvaluationSnapshotServiceImpl.class.getDeclaredMethod("canonicalJson", String.class);
        canonical.setAccessible(true);
        String first = (String) canonical.invoke(service, "{\"z\":1,\"nested\":{\"b\":2,\"a\":1},\"items\":[{\"b\":2,\"a\":1},3]}");
        String second = (String) canonical.invoke(service, "{\"items\":[{\"a\":1,\"b\":2},3],\"nested\":{\"a\":1,\"b\":2},\"z\":1}");
        assertEquals(first, second);
        assertEquals("{\"items\":[{\"a\":1,\"b\":2},3],\"nested\":{\"a\":1,\"b\":2},\"z\":1}", first);
    }
}
