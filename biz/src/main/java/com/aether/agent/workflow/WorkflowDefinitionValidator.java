package com.aether.agent.workflow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.exception.ServerException;
import org.apache.commons.lang3.StringUtils;
import java.util.*;

/** 首版顺序流程校验，发布前拒绝循环、孤立节点和不可用配置。 */
public final class WorkflowDefinitionValidator {
    private static final Set<String> TYPES = new HashSet<String>(Arrays.asList("start", "agent", "mcp", "human", "end"));
    private WorkflowDefinitionValidator() { }

    public static void validate(String nodesText, String edgesText) {
        JSONArray nodes;
        JSONArray edges;
        try { nodes = JSONArray.parseArray(nodesText); edges = JSONArray.parseArray(edgesText); }
        catch (Exception e) { throw new ServerException(422, "工作流画布不是有效 JSON"); }
        if (nodes == null || nodes.isEmpty()) throw new ServerException(422, "工作流至少需要一个节点");
        Map<String, JSONObject> map = new LinkedHashMap<String, JSONObject>();
        int starts = 0, ends = 0;
        for (Object value : nodes) {
            JSONObject node = (JSONObject) value;
            String id = node.getString("id"), type = node.getString("type");
            if (StringUtils.isBlank(id) || !TYPES.contains(type) || map.put(id, node) != null)
                throw new ServerException(422, "节点 ID 或节点类型无效");
            if ("start".equals(type)) starts++;
            if ("end".equals(type)) ends++;
            if (("agent".equals(type) || "mcp".equals(type)) && StringUtils.isBlank(node.getString("resourceId")))
                throw new ServerException(422, type + " 节点必须选择可用资源");
        }
        if (starts != 1 || ends != 1) throw new ServerException(422, "工作流必须且只能包含一个开始节点和一个结束节点");
        Map<String, String> next = new HashMap<String, String>();
        Map<String, Integer> in = new HashMap<String, Integer>();
        for (Object value : edges) {
            JSONObject edge = (JSONObject) value;
            String source = edge.getString("source"), target = edge.getString("target");
            if (!map.containsKey(source) || !map.containsKey(target) || source.equals(target) || next.put(source, target) != null)
                throw new ServerException(422, "首版只支持有效的单入单出顺序连线");
            int count = in.containsKey(target) ? in.get(target) + 1 : 1;
            if (count > 1) throw new ServerException(422, "首版只支持单入单出顺序连线");
            in.put(target, count);
        }
        String current = null;
        for (JSONObject node : map.values()) if ("start".equals(node.getString("type"))) current = node.getString("id");
        Set<String> visited = new HashSet<String>(); String last = null;
        while (current != null && visited.add(current)) { last = current; current = next.get(current); }
        if (visited.size() != map.size() || current != null) throw new ServerException(422, "流程必须从开始节点顺序连通至结束节点，且不能包含循环或孤立节点");
        JSONObject end = map.get(last);
        if (!"end".equals(end.getString("type"))) throw new ServerException(422, "流程必须以结束节点收尾");
    }

    public static List<JSONObject> orderedNodes(String nodesText, String edgesText) {
        validate(nodesText, edgesText);
        JSONArray nodes = JSONArray.parseArray(nodesText), edges = JSONArray.parseArray(edgesText);
        Map<String, JSONObject> map = new HashMap<String, JSONObject>(); Map<String, String> next = new HashMap<String, String>();
        String current = null;
        for (Object value : nodes) { JSONObject node = (JSONObject) value; map.put(node.getString("id"), node); if ("start".equals(node.getString("type"))) current = node.getString("id"); }
        for (Object value : edges) { JSONObject edge = (JSONObject) value; next.put(edge.getString("source"), edge.getString("target")); }
        List<JSONObject> result = new ArrayList<JSONObject>(); while (current != null) { result.add(map.get(current)); current = next.get(current); } return result;
    }
}
