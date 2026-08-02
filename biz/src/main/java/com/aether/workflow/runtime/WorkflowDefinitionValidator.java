package com.aether.workflow.runtime;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.exception.ServerException;
import org.apache.commons.lang3.StringUtils;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流定义校验器。支持顺序、连线条件分支和循环流程。
 * <ul>
 *   <li>必须且只有一个开始节点和一个结束节点</li>
 *   <li>任何节点可有多条出边，每条出边可携带 condition 表达式（运行时按条件选择走向）</li>
 *   <li>允许回跳边（循环），每条回跳边可设置 maxIterations（默认 10）</li>
 *   <li>从开始节点可达所有节点，且结束节点可达</li>
 * </ul>
 */
public final class WorkflowDefinitionValidator {
    private static final Set<String> TYPES = new HashSet<String>(Arrays.asList("start", "agent", "mcp", "human", "end"));
    private static final Pattern VARIABLE_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Pattern VARIABLE_REFERENCE = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)}");
    private WorkflowDefinitionValidator() { }

    // ── 公共入口 ──────────────────────────────────────────────

    public static void validate(String nodesText, String edgesText) {
        JSONArray nodes = parseJsonArray(nodesText, "工作流画布不是有效 JSON");
        JSONArray edges = parseJsonArray(edgesText, "工作流边数据不是有效 JSON");
        if (nodes == null || nodes.isEmpty()) throw new ServerException(422, "工作流至少需要一个节点");

        Map<String, JSONObject> nodeMap = buildNodeMap(nodes);
        int starts = 0, ends = 0;
        for (JSONObject node : nodeMap.values()) {
            String type = node.getString("type");
            if ("start".equals(type)) starts++;
            if ("end".equals(type)) ends++;
            if (("agent".equals(type) || "mcp".equals(type)) && StringUtils.isBlank(node.getString("resourceId")))
                throw new ServerException(422, type + " 节点必须选择可用资源");
        }
        if (starts != 1 || ends != 1) throw new ServerException(422, "工作流必须且只能包含一个开始节点和一个结束节点");

        // source → 出边列表，target → 入边节点列表
        Map<String, List<JSONObject>> outEdges = new LinkedHashMap<String, List<JSONObject>>();
        Map<String, List<String>> inNodes = new LinkedHashMap<String, List<String>>();
        List<JSONObject> edgeList = new ArrayList<JSONObject>();

        for (Object value : edges) {
            JSONObject edge = (JSONObject) value;
            String source = edge.getString("source"), target = edge.getString("target");
            if (StringUtils.isBlank(source) || StringUtils.isBlank(target))
                throw new ServerException(422, "边必须包含 source 和 target");
            if (!nodeMap.containsKey(source) || !nodeMap.containsKey(target))
                throw new ServerException(422, "边引用了不存在的节点");
            if (source.equals(target)) throw new ServerException(422, "不允许自环边");
            edgeList.add(edge);
            outEdges.computeIfAbsent(source, k -> new ArrayList<JSONObject>()).add(edge);
            inNodes.computeIfAbsent(target, k -> new ArrayList<String>()).add(source);
        }

        String endId = findEndId(nodeMap);
        // 结束节点不能有出边，避免运行通过结束节点后继续执行。
        for (Map.Entry<String, JSONObject> entry : nodeMap.entrySet()) {
            String nodeId = entry.getKey();
            String type = entry.getValue().getString("type");
            List<JSONObject> outs = outEdges.getOrDefault(nodeId, Collections.<JSONObject>emptyList());
            if ("end".equals(type) && !outs.isEmpty())
                throw new ServerException(422, "结束节点不能包含出边");
        }

        // 检测回跳边并校验 maxIterations（按节点出现顺序编号）
        String startId = findStartId(nodeMap);
        int idx = 0;
        Map<String, Integer> nodeOrder = new LinkedHashMap<String, Integer>();
        for (String nid : nodeMap.keySet()) nodeOrder.put(nid, idx++);

        for (JSONObject edge : edgeList) {
            String src = edge.getString("source"), tgt = edge.getString("target");
            if (nodeOrder.containsKey(src) && nodeOrder.containsKey(tgt) && nodeOrder.get(tgt) <= nodeOrder.get(src)) {
                // 回跳边
                int maxIter = edge.getIntValue("maxIterations");
                if (maxIter <= 0) maxIter = 10;
                if (maxIter > 100) throw new ServerException(422, "循环边最大迭代次数不能超过 100");
            }
        }

        // 从 start BFS 检查可达性
        Set<String> reachable = new HashSet<String>();
        Queue<String> queue = new LinkedList<String>();
        queue.add(startId);
        reachable.add(startId);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (JSONObject e : outEdges.getOrDefault(cur, Collections.<JSONObject>emptyList())) {
                String tgt = e.getString("target");
                if (reachable.add(tgt)) queue.add(tgt);
            }
        }
        // 也要把回跳边的目标纳入可达集合（循环内节点可能只通过回跳边可达的情况不存在，因为至少有一条正向入边）
        if (reachable.size() != nodeMap.size()) {
            for (String nid : nodeMap.keySet()) {
                if (!reachable.contains(nid))
                    throw new ServerException(422, "节点 [" + nid + "] 从开始节点不可达");
            }
        }

        // 每个可达节点都必须存在一条到结束节点的路径，避免分支走到死路后被误标记为完成。
        if (!reachable.contains(endId)) throw new ServerException(422, "结束节点从开始节点不可达");
        Set<String> canReachEnd = new HashSet<String>();
        Queue<String> reverseQueue = new LinkedList<String>();
        reverseQueue.add(endId);
        canReachEnd.add(endId);
        while (!reverseQueue.isEmpty()) {
            String current = reverseQueue.poll();
            for (String previous : inNodes.getOrDefault(current, Collections.<String>emptyList())) {
                if (canReachEnd.add(previous)) reverseQueue.add(previous);
            }
        }
        for (String nodeId : reachable) {
            if (!canReachEnd.contains(nodeId))
                throw new ServerException(422, "节点 [" + nodeId + "] 无法到达结束节点");
        }
    }

    /**
     * 校验启动表单及节点内变量引用。结构校验与变量契约分开保留，方便旧调用方逐步迁移。
     * 节点输出键、状态映射键及内部键均视为流程可用变量；引用不存在的变量将拒绝发布，
     * 从而避免运行到一半才发现提示词或工具参数中的拼写错误。
     */
    public static void validateVariables(String nodesText, String edgesText, String inputSchemaText) {
        JSONArray nodes = parseJsonArray(nodesText, "工作流画布不是有效 JSON");
        JSONArray edges = parseJsonArray(edgesText, "工作流边数据不是有效 JSON");
        JSONArray schema = parseJsonArray(inputSchemaText, "开始表单字段不是有效 JSON");
        Set<String> declared = new LinkedHashSet<String>();
        for (Object value : schema) {
            if (!(value instanceof JSONObject)) throw new ServerException(422, "开始表单字段必须是对象数组");
            String name = ((JSONObject) value).getString("name");
            if (StringUtils.isBlank(name) || !VARIABLE_NAME.matcher(name).matches())
                throw new ServerException(422, "开始表单变量名无效: " + name);
            if (!declared.add(name)) throw new ServerException(422, "开始表单变量名重复: " + name);
        }
        Map<String, Set<String>> availableBefore = availableVariablesBefore(nodes, edges, declared);
        // 使用所有入边均能提供的变量做校验，避免引用后续或另一分支才产生的输出。
        for (Object value : nodes) {
            JSONObject node = (JSONObject) value;
            Set<String> available = availableBefore.get(node.getString("id"));
            validateReferences(node.getString("prompt"), available, node.getString("id"));
            validateReferences(node.getString("argumentsTemplate"), available, node.getString("id"));
            validateReferences(node.getString("question"), available, node.getString("id"));
        }
    }

    /**
     * 校验业务回调输出契约。输出字段必须在到达结束节点的每条路径上都已经存在，
     * 防止分支流程只在部分路径返回该字段而让业务系统收到不稳定的数据结构。
     */
    public static void validateOutputSchema(String nodesText, String edgesText, String inputSchemaText, String outputSchemaText) {
        JSONArray nodes = parseJsonArray(nodesText, "工作流画布不是有效 JSON");
        JSONArray edges = parseJsonArray(edgesText, "工作流边数据不是有效 JSON");
        JSONArray inputSchema = parseJsonArray(inputSchemaText, "开始表单字段不是有效 JSON");
        JSONArray outputSchema = parseJsonArray(outputSchemaText, "最终输出字段不是有效 JSON");
        Set<String> declared = schemaNames(inputSchema, "开始表单");
        Set<String> outputs = schemaNames(outputSchema, "最终输出");
        Map<String, Set<String>> availableBefore = availableVariablesBefore(nodes, edges, declared);
        String endId = null;
        for (Object value : nodes) {
            JSONObject node = (JSONObject) value;
            if ("end".equals(node.getString("type"))) { endId = node.getString("id"); break; }
        }
        Set<String> terminalVariables = endId == null ? Collections.<String>emptySet() : availableBefore.get(endId);
        for (String output : outputs) {
            if (terminalVariables == null || !terminalVariables.contains(output))
                throw new ServerException(422, "最终输出变量在所有结束路径上不可用: " + output);
        }
    }

    private static Set<String> schemaNames(JSONArray schema, String schemaName) {
        Set<String> names = new LinkedHashSet<String>();
        for (Object value : schema) {
            if (!(value instanceof JSONObject)) throw new ServerException(422, schemaName + "字段必须是对象数组");
            String name = ((JSONObject) value).getString("name");
            if (StringUtils.isBlank(name) || !VARIABLE_NAME.matcher(name).matches())
                throw new ServerException(422, schemaName + "变量名无效: " + name);
            if (!names.add(name)) throw new ServerException(422, schemaName + "变量名重复: " + name);
        }
        return names;
    }

    /** 计算每个节点执行前、所有入边共同保证存在的变量集合。 */
    private static Map<String, Set<String>> availableVariablesBefore(JSONArray nodes, JSONArray edges, Set<String> declared) {
        Map<String, Set<String>> produced = new LinkedHashMap<String, Set<String>>();
        Map<String, List<String>> predecessors = new LinkedHashMap<String, List<String>>();
        for (Object value : nodes) {
            JSONObject node = (JSONObject) value;
            Set<String> nodeProduced = new LinkedHashSet<String>();
            addVariable(nodeProduced, node.getString("outputKey"), "节点输出变量名无效");
            addVariable(nodeProduced, node.getString("internalKey"), "节点内部变量名无效");
            String mapping = node.getString("stateMapping");
            if (StringUtils.isNotBlank(mapping)) {
                try {
                    JSONObject map = JSONObject.parseObject(mapping);
                    for (String key : map.keySet()) addVariable(nodeProduced, key, "状态映射变量名无效");
                } catch (Exception e) {
                    throw new ServerException(422, "节点状态映射必须是 JSON 对象");
                }
            }
            produced.put(node.getString("id"), nodeProduced);
        }
        for (Object value : nodes) predecessors.put(((JSONObject) value).getString("id"), new ArrayList<String>());
        for (Object value : edges) {
            JSONObject edge = (JSONObject) value;
            List<String> incoming = predecessors.get(edge.getString("target"));
            if (incoming != null) incoming.add(edge.getString("source"));
        }
        Set<String> allVariables = new LinkedHashSet<String>(declared);
        for (Set<String> nodeProduced : produced.values()) allVariables.addAll(nodeProduced);
        Map<String, Set<String>> availableBefore = new LinkedHashMap<String, Set<String>>();
        for (Object value : nodes) {
            JSONObject node = (JSONObject) value;
            String nodeId = node.getString("id");
            availableBefore.put(nodeId, "start".equals(node.getString("type"))
                    ? new LinkedHashSet<String>(declared) : new LinkedHashSet<String>(allVariables));
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Object value : nodes) {
                JSONObject node = (JSONObject) value;
                String nodeId = node.getString("id");
                if ("start".equals(node.getString("type"))) continue;
                List<String> incoming = predecessors.get(nodeId);
                Set<String> next = new LinkedHashSet<String>(declared);
                if (incoming != null && !incoming.isEmpty()) {
                    next = null;
                    for (String previous : incoming) {
                        Set<String> after = new LinkedHashSet<String>(availableBefore.get(previous));
                        after.addAll(produced.get(previous));
                        if (next == null) next = after;
                        else next.retainAll(after);
                    }
                    if (next == null) next = new LinkedHashSet<String>(declared);
                }
                if (!next.equals(availableBefore.get(nodeId))) {
                    availableBefore.put(nodeId, next);
                    changed = true;
                }
            }
        }
        return availableBefore;
    }

    /** 校验手动启动传入的变量：必填字段必须有非空值，禁止传入未声明字段。 */
    public static void validateStartVariables(String inputSchemaText, Map<String, Object> variables) {
        JSONArray schema = parseJsonArray(inputSchemaText, "开始表单字段不是有效 JSON");
        Map<String, Object> input = variables == null ? Collections.<String, Object>emptyMap() : variables;
        Set<String> names = new LinkedHashSet<String>();
        for (Object value : schema) {
            if (!(value instanceof JSONObject)) throw new ServerException(422, "开始表单字段必须是对象数组");
            JSONObject field = (JSONObject) value;
            String name = field.getString("name");
            if (StringUtils.isBlank(name) || !VARIABLE_NAME.matcher(name).matches())
                throw new ServerException(422, "开始表单变量名无效: " + name);
            names.add(name);
            Object inputValue = input.get(name);
            if (field.getBooleanValue("required") && (inputValue == null || StringUtils.isBlank(String.valueOf(inputValue))))
                throw new ServerException(422, "开始表单必填字段未填写: " + name);
        }
        for (String name : input.keySet()) {
            if (!names.contains(name)) throw new ServerException(422, "开始表单不包含变量: " + name);
        }
    }

    private static void addVariable(Set<String> variables, String name, String errorPrefix) {
        if (StringUtils.isBlank(name)) return;
        if (!VARIABLE_NAME.matcher(name).matches()) throw new ServerException(422, errorPrefix + ": " + name);
        variables.add(name);
    }

    private static void validateReferences(String template, Set<String> available, String nodeId) {
        if (StringUtils.isBlank(template)) return;
        Matcher matcher = VARIABLE_REFERENCE.matcher(template);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!available.contains(name))
                throw new ServerException(422, "节点 [" + nodeId + "] 引用了未声明变量: " + name);
        }
    }

    /** 返回按执行拓扑排序的节点列表（供执行引擎顺序遍历使用）。 */
    public static List<JSONObject> orderedNodes(String nodesText, String edgesText) {
        JSONArray nodes = parseJsonArray(nodesText, "工作流画布不是有效 JSON");
        JSONArray edges = parseJsonArray(edgesText, "工作流边数据不是有效 JSON");
        Map<String, JSONObject> nodeMap = buildNodeMap(nodes);

        Map<String, List<JSONObject>> outEdges = new LinkedHashMap<String, List<JSONObject>>();
        for (Object value : edges) {
            JSONObject edge = (JSONObject) value;
            outEdges.computeIfAbsent(edge.getString("source"), k -> new ArrayList<JSONObject>()).add(edge);
        }

        String startId = findStartId(nodeMap);
        List<JSONObject> result = new ArrayList<JSONObject>();
        Set<String> visited = new HashSet<String>();
        dfsTopo(startId, nodeMap, outEdges, visited, result, new HashSet<String>());
        // 补充未访问节点（不应出现，但防御性处理）
        for (JSONObject node : nodeMap.values()) {
            if (!visited.contains(node.getString("id"))) result.add(node);
        }
        return result;
    }

    /** 构建邻接表（source → 边列表），供执行引擎使用。 */
    public static Map<String, List<JSONObject>> buildAdjacency(String edgesText) {
        JSONArray edges = parseJsonArray(edgesText, "工作流边数据不是有效 JSON");
        Map<String, List<JSONObject>> adj = new LinkedHashMap<String, List<JSONObject>>();
        for (Object value : edges) {
            JSONObject edge = (JSONObject) value;
            adj.computeIfAbsent(edge.getString("source"), k -> new ArrayList<JSONObject>()).add(edge);
        }
        return adj;
    }

    // ── 内部方法 ─────────────────────────────────────────────

    private static JSONArray parseJsonArray(String text, String errorMsg) {
        if (StringUtils.isBlank(text)) return new JSONArray();
        try { return JSONArray.parseArray(text); } catch (Exception e) { throw new ServerException(422, errorMsg); }
    }

    private static Map<String, JSONObject> buildNodeMap(JSONArray nodes) {
        Map<String, JSONObject> map = new LinkedHashMap<String, JSONObject>();
        for (Object value : nodes) {
            JSONObject node = (JSONObject) value;
            String id = node.getString("id"), type = node.getString("type");
            if (StringUtils.isBlank(id) || !TYPES.contains(type) || map.put(id, node) != null)
                throw new ServerException(422, "节点 ID 或节点类型无效");
        }
        return map;
    }

    private static String findStartId(Map<String, JSONObject> nodeMap) {
        for (Map.Entry<String, JSONObject> e : nodeMap.entrySet()) if ("start".equals(e.getValue().getString("type"))) return e.getKey();
        throw new ServerException(422, "缺少开始节点");
    }

    private static String findEndId(Map<String, JSONObject> nodeMap) {
        for (Map.Entry<String, JSONObject> e : nodeMap.entrySet()) if ("end".equals(e.getValue().getString("type"))) return e.getKey();
        throw new ServerException(422, "缺少结束节点");
    }

    /** DFS 拓扑排序，跳过回跳边（已访问节点），保证 DAG 部分正确排序。 */
    private static void dfsTopo(String nodeId, Map<String, JSONObject> nodeMap, Map<String, List<JSONObject>> outEdges,
                                Set<String> visited, List<JSONObject> result, Set<String> inStack) {
        if (inStack.contains(nodeId)) return; // 遇到当前 DFS 路径上的节点 → 回跳边，跳过
        if (visited.contains(nodeId)) return;
        visited.add(nodeId);
        inStack.add(nodeId);
        JSONObject node = nodeMap.get(nodeId);
        if (node != null) {
            List<JSONObject> outs = outEdges.getOrDefault(nodeId, Collections.<JSONObject>emptyList());
            for (JSONObject edge : outs) dfsTopo(edge.getString("target"), nodeMap, outEdges, visited, result, inStack);
            result.add(node);
        }
        inStack.remove(nodeId);
    }
}
