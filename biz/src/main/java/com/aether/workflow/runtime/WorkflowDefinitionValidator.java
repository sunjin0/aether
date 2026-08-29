package com.aether.workflow.runtime;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
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
    private static final Set<String> TYPES = new HashSet<String>(Arrays.asList(
            "start", "agent", "tool", "human", "approval", "rule", "transform", "http", "notification", "subflow", "parallel", "join", "wait_event", "delay", "end"));
    private static final Pattern VARIABLE_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Pattern VARIABLE_REFERENCE = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)}");
    /**
 * 创建 {@code WorkflowDefinitionValidator} 实例。
 */
private WorkflowDefinitionValidator() { }

    // ── 公共入口 ──────────────────────────────────────────────

    /**
 * 校验当前请求。
 */
public static void validate(String nodesText, String edgesText) {
        JSONArray nodes = parseJsonArray(nodesText, "workflow.definition.canvas.json.invalid");
        JSONArray edges = parseJsonArray(edgesText, "workflow.definition.edges.json.invalid");
        if (nodes == null || nodes.isEmpty()) throw new ServerException(422, I18nUtils.getMessage("workflow.definition.nodes.required"));

        Map<String, JSONObject> nodeMap = buildNodeMap(nodes);
        int starts = 0, ends = 0;
        for (JSONObject node : nodeMap.values()) {
            String type = node.getString("type");
            if ("start".equals(type)) starts++;
            if ("end".equals(type)) ends++;
            if (("agent".equals(type) || "tool".equals(type)) && StringUtils.isBlank(node.getString("resourceId")))
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.node.resource.required", new Object[]{type}));
            if ("tool".equals(type)) {
                String policy = node.getString("toolApprovalPolicy");
                if (StringUtils.isNotBlank(policy) && !"ask".equals(policy) && !"risky".equals(policy) && !"never".equals(policy))
                    throw new ServerException(422, "工具节点 toolApprovalPolicy 仅支持 ask/risky/never：" + node.getString("id"));
            }
            if ("transform".equals(type) && (node.getJSONArray("mappings") == null || node.getJSONArray("mappings").isEmpty()))
                throw new ServerException(422, "数据转换节点必须配置 mappings");
            if ("http".equals(type) && StringUtils.isBlank(node.getString("url")))
                throw new ServerException(422, "HTTP 节点必须配置 url");
            if ("notification".equals(type)) {
                String channel = StringUtils.defaultIfBlank(node.getString("channel"), "email");
                if (!"email".equalsIgnoreCase(channel))
                    throw new ServerException(422, "通知节点仅支持 email 渠道：" + node.getString("id"));
                if (StringUtils.isBlank(node.getString("toTemplate")))
                    throw new ServerException(422, "通知节点必须配置 toTemplate");
            }
            if ("subflow".equals(type)) {
                if (StringUtils.isBlank(node.getString("workflowId")))
                    throw new ServerException(422, "子流程节点必须配置 workflowId");
                if (node.getIntValue("versionNo") <= 0)
                    throw new ServerException(422, "子流程节点必须配置固定 versionNo");
                if (node.containsKey("timeoutMillis") && node.getLongValue("timeoutMillis") <= 0)
                    throw new ServerException(422, "子流程 timeoutMillis 必须大于 0");
            }
            if ("parallel".equals(type)) {
                JSONArray branches = node.getJSONArray("branches");
                if (branches == null || branches.isEmpty()) throw new ServerException(422, "并行节点必须配置 branches");
                if (node.containsKey("maxBranches") && (node.getIntValue("maxBranches") <= 0 || node.getIntValue("maxBranches") > 50))
                    throw new ServerException(422, "并行节点 maxBranches 必须在 1 到 50 之间");
                if (node.containsKey("branchTimeoutMillis") && node.getLongValue("branchTimeoutMillis") <= 0)
                    throw new ServerException(422, "并行节点 branchTimeoutMillis 必须大于 0");
                for (Object branch : branches) {
                    if (!(branch instanceof String) || StringUtils.isBlank(String.valueOf(branch)))
                        throw new ServerException(422, "并行分支入口必须是节点 ID");
                }
            }
            if ("join".equals(type) && StringUtils.isNotBlank(node.getString("joinMode"))
                    && !Arrays.asList("ALL_SUCCESS", "ANY_SUCCESS", "ALLOW_PARTIAL_FAILURE").contains(node.getString("joinMode")))
                throw new ServerException(422, "汇聚节点 joinMode 不支持");
            if ("wait_event".equals(type)) {
                if (StringUtils.isBlank(node.getString("eventType")))
                    throw new ServerException(422, "等待事件节点必须配置 eventType");
                // 事件按类型和关联键匹配。缺少关联键会使同类型的事件唤醒所有等待实例，
                // 因此在发布时拒绝这种无法安全路由的定义。
                if (StringUtils.isBlank(node.getString("correlationKeyTemplate")))
                    throw new ServerException(422, "等待事件节点必须配置 correlationKeyTemplate");
                if (node.containsKey("timeoutMillis") && node.getLongValue("timeoutMillis") <= 0)
                    throw new ServerException(422, "等待事件节点 timeoutMillis 必须大于 0");
                if (node.containsKey("timeoutMillis") && StringUtils.isBlank(node.getString("timeoutTargetId")))
                    throw new ServerException(422, "等待事件节点配置 timeoutMillis 时必须配置 timeoutTargetId");
            }
            if ("delay".equals(type) && node.getLongValue("delayMillis") <= 0)
                throw new ServerException(422, "延时节点必须配置大于 0 的 delayMillis");
            if ("approval".equals(type) && StringUtils.isNotBlank(node.getString("approvalMode"))
                    && !"ANY".equals(node.getString("approvalMode")))
                throw new ServerException(422, "当前审批节点仅支持 ANY 审批模式");
        }
        for (JSONObject node : nodeMap.values()) {
            if (!"wait_event".equals(node.getString("type")) || !node.containsKey("timeoutMillis")) continue;
            String timeoutTargetId = node.getString("timeoutTargetId");
            if (!nodeMap.containsKey(timeoutTargetId))
                throw new ServerException(422, "等待事件节点 timeoutTargetId 必须指向已有节点：" + timeoutTargetId);
        }
        if (starts != 1 || ends != 1) throw new ServerException(422, I18nUtils.getMessage("workflow.definition.start-end.count.invalid"));

        // source → 出边列表，target → 入边节点列表
        Map<String, List<JSONObject>> outEdges = new LinkedHashMap<String, List<JSONObject>>();
        Map<String, List<String>> inNodes = new LinkedHashMap<String, List<String>>();
        List<JSONObject> edgeList = new ArrayList<JSONObject>();

        for (Object value : edges) {
            JSONObject edge = (JSONObject) value;
            String source = edge.getString("source"), target = edge.getString("target");
            if (StringUtils.isBlank(source) || StringUtils.isBlank(target))
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.edge.source-target.required"));
            if (!nodeMap.containsKey(source) || !nodeMap.containsKey(target))
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.edge.node.not-found"));
            if (source.equals(target)) throw new ServerException(422, I18nUtils.getMessage("workflow.definition.edge.self-loop.disallowed"));
            edgeList.add(edge);
            outEdges.computeIfAbsent(source, k -> new ArrayList<JSONObject>()).add(edge);
            inNodes.computeIfAbsent(target, k -> new ArrayList<String>()).add(source);
        }

        // 并行节点必须能够汇聚到同一个 join，避免分支永远无法合流。
        for (JSONObject parallel : nodeMap.values()) {
            if (!"parallel".equals(parallel.getString("type"))) continue;
            String configuredJoin = parallel.getString("joinNodeId");
            if (StringUtils.isNotBlank(configuredJoin)) {
                JSONObject join = nodeMap.get(configuredJoin);
                if (join == null || !"join".equals(join.getString("type")))
                    throw new ServerException(422, "并行节点 joinNodeId 必须指向汇聚节点：" + configuredJoin);
                continue;
            }
            JSONArray branches = parallel.getJSONArray("branches");
            Set<String> common = null;
            for (Object branch : branches) {
                Set<String> reachable = reachableNodes(String.valueOf(branch), outEdges);
                if (common == null) common = reachable; else common.retainAll(reachable);
            }
            boolean hasJoin = false;
            if (common != null) for (String candidate : common) {
                JSONObject n = nodeMap.get(candidate);
                if (n != null && "join".equals(n.getString("type"))) { hasJoin = true; break; }
            }
            if (!hasJoin) throw new ServerException(422, "并行节点必须配置 joinNodeId 或让所有分支汇聚到同一 join");
        }

        String endId = findEndId(nodeMap);
        // 结束节点不能有出边，避免运行通过结束节点后继续执行。
        for (Map.Entry<String, JSONObject> entry : nodeMap.entrySet()) {
            String nodeId = entry.getKey();
            String type = entry.getValue().getString("type");
            List<JSONObject> outs = outEdges.getOrDefault(nodeId, Collections.<JSONObject>emptyList());
            if ("end".equals(type) && !outs.isEmpty())
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.end-node.outgoing-edge.disallowed"));
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
                if (maxIter > 100) throw new ServerException(422, I18nUtils.getMessage("workflow.definition.loop.max-iterations.exceeded"));
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
                    throw new ServerException(422, I18nUtils.getMessage("workflow.definition.node.unreachable-from-start", new Object[]{nid}));
            }
        }

        // 每个可达节点都必须存在一条到结束节点的路径，避免分支走到死路后被误标记为完成。
        if (!reachable.contains(endId)) throw new ServerException(422, I18nUtils.getMessage("workflow.definition.end-node.unreachable-from-start"));
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
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.node.cannot-reach-end", new Object[]{nodeId}));
        }
    }

    private static Set<String> reachableNodes(String start, Map<String, List<JSONObject>> outEdges) {
        Set<String> visited = new LinkedHashSet<String>();
        Deque<String> queue = new ArrayDeque<String>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) continue;
            for (JSONObject edge : outEdges.getOrDefault(current, Collections.<JSONObject>emptyList()))
                queue.addLast(edge.getString("target"));
        }
        return visited;
    }

    /**
     * 校验启动表单及节点内变量引用。结构校验与变量契约分开保留，方便旧调用方逐步迁移。
     * 节点输出键、状态映射键及内部键均视为流程可用变量；引用不存在的变量将拒绝发布，
     * 从而避免运行到一半才发现提示词或工具参数中的拼写错误。
     */
    public static void validateVariables(String nodesText, String edgesText, String inputSchemaText) {
        JSONArray nodes = parseJsonArray(nodesText, "workflow.definition.canvas.json.invalid");
        JSONArray edges = parseJsonArray(edgesText, "workflow.definition.edges.json.invalid");
        JSONArray schema = parseJsonArray(inputSchemaText, "workflow.definition.start-form.json.invalid");
        Set<String> declared = new LinkedHashSet<String>();
        for (Object value : schema) {
            if (!(value instanceof JSONObject)) throw new ServerException(422, I18nUtils.getMessage("workflow.definition.start-form.fields.invalid"));
            String name = ((JSONObject) value).getString("name");
            if (StringUtils.isBlank(name) || !VARIABLE_NAME.matcher(name).matches())
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.start-form.variable-name.invalid", new Object[]{name}));
            if (!declared.add(name)) throw new ServerException(422, I18nUtils.getMessage("workflow.definition.start-form.variable-name.duplicate", new Object[]{name}));
        }
        Map<String, Set<String>> availableBefore = availableVariablesBefore(nodes, edges, declared);
        // 使用所有入边均能提供的变量做校验，避免引用后续或另一分支才产生的输出。
        for (Object value : nodes) {
            JSONObject node = (JSONObject) value;
            Set<String> available = availableBefore.get(node.getString("id"));
            validateReferences(node.getString("prompt"), available, node.getString("id"));
            validateReferences(node.getString("argumentsTemplate"), available, node.getString("id"));
            validateReferences(node.getString("question"), available, node.getString("id"));
            validateReferences(node.getString("url"), available, node.getString("id"));
            validateReferences(node.getString("bodyTemplate"), available, node.getString("id"));
            validateReferences(node.getString("toTemplate"), available, node.getString("id"));
            validateReferences(node.getString("subjectTemplate"), available, node.getString("id"));
            JSONArray subflowInputMappings = node.getJSONArray("inputMappings");
            if (subflowInputMappings != null) for (Object mappingValue : subflowInputMappings) {
                if (!(mappingValue instanceof JSONObject)) throw new ServerException(422, "子流程输入映射必须是对象数组");
                JSONObject mapping = (JSONObject) mappingValue;
                String target = mapping.getString("target");
                if (StringUtils.isBlank(target) || !VARIABLE_NAME.matcher(target).matches())
                    throw new ServerException(422, "子流程输入目标变量名不合法：" + target);
                validateReferences(mapping.getString("template"), available, node.getString("id"));
            String source = mapping.getString("source");
                String sourceRoot = sourceRoot(source);
                if (StringUtils.isNotBlank(sourceRoot) && !available.contains(sourceRoot))
                    throw new ServerException(422, I18nUtils.getMessage("workflow.variable.not-provided", new Object[]{sourceRoot}));
            }
            if ("parallel".equals(node.getString("type"))) {
                JSONArray branches = node.getJSONArray("branches");
                for (Object branch : branches) {
                    String branchId = String.valueOf(branch);
                    JSONObject branchNode = null;
                    for (Object candidate : nodes) if (branchId.equals(((JSONObject) candidate).getString("id"))) { branchNode = (JSONObject) candidate; break; }
                    if (branchNode == null) throw new ServerException(422, "并行分支入口不存在：" + branchId);
                    String branchType = branchNode.getString("type");
                    if (Arrays.asList("agent", "tool", "human", "approval", "subflow", "wait_event", "delay").contains(branchType))
                        throw new ServerException(422, "并行分支暂不支持交互或等待节点：" + branchId);
                }
            }
            validateReferences(node.getString("idempotencyKeyTemplate"), available, node.getString("id"));
            validateReferences(node.getString("correlationKeyTemplate"), available, node.getString("id"));
            JSONArray mappings = node.getJSONArray("mappings");
            if (mappings != null) for (Object mappingValue : mappings) {
                if (!(mappingValue instanceof JSONObject))
                    throw new ServerException(422, "数据转换节点 mappings 必须是对象数组");
                JSONObject mapping = (JSONObject) mappingValue;
                String target = mapping.getString("target");
                if (StringUtils.isBlank(target) || !VARIABLE_NAME.matcher(target).matches())
                    throw new ServerException(422, "数据转换目标变量名不合法：" + target);
                validateReferences(mapping.getString("template"), available, node.getString("id"));
                String source = mapping.getString("source");
                String sourceRoot = sourceRoot(source);
                if (StringUtils.isNotBlank(sourceRoot) && !available.contains(sourceRoot))
                    throw new ServerException(422, I18nUtils.getMessage("workflow.variable.not-provided", new Object[]{sourceRoot}));
            }
        }
    }

    /**
     * 校验业务回调输出契约。输出字段必须在到达结束节点的每条路径上都已经存在，
     * 防止分支流程只在部分路径返回该字段而让业务系统收到不稳定的数据结构。
     */
    public static void validateOutputSchema(String nodesText, String edgesText, String inputSchemaText, String outputSchemaText) {
        JSONArray nodes = parseJsonArray(nodesText, "workflow.definition.canvas.json.invalid");
        JSONArray edges = parseJsonArray(edgesText, "workflow.definition.edges.json.invalid");
        JSONArray inputSchema = parseJsonArray(inputSchemaText, "workflow.definition.start-form.json.invalid");
        JSONArray outputSchema = parseJsonArray(outputSchemaText, "workflow.definition.output-schema.json.invalid");
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
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.output.variable.unavailable", new Object[]{output}));
        }
    }

    /**
 * 处理schemaNames。
 */
private static Set<String> schemaNames(JSONArray schema, String schemaName) {
        Set<String> names = new LinkedHashSet<String>();
        for (Object value : schema) {
            if (!(value instanceof JSONObject)) throw new ServerException(422, I18nUtils.getMessage("workflow.definition.schema.fields.invalid", new Object[]{schemaName}));
            String name = ((JSONObject) value).getString("name");
            if (StringUtils.isBlank(name) || !VARIABLE_NAME.matcher(name).matches())
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.schema.variable-name.invalid", new Object[]{schemaName, name}));
            if (!names.add(name)) throw new ServerException(422, I18nUtils.getMessage("workflow.definition.schema.variable-name.duplicate", new Object[]{schemaName, name}));
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
            addVariable(nodeProduced, node.getString("outputKey"), "workflow.definition.node.output-variable-name.invalid");
            addVariable(nodeProduced, node.getString("internalKey"), "workflow.definition.node.internal-variable-name.invalid");
            JSONArray mappings = node.getJSONArray("mappings");
            if (mappings != null) for (Object mappingValue : mappings) {
                if (mappingValue instanceof JSONObject)
                    addVariable(nodeProduced, ((JSONObject) mappingValue).getString("target"), "workflow.definition.node.output-variable-name.invalid");
            }
            String mapping = node.getString("stateMapping");
            if (StringUtils.isNotBlank(mapping)) {
                try {
                    JSONObject map = JSONObject.parseObject(mapping);
                    for (String key : map.keySet()) addVariable(nodeProduced, key, "workflow.definition.node.state-mapping-variable-name.invalid");
                } catch (Exception e) {
                    throw new ServerException(422, I18nUtils.getMessage("workflow.definition.node-status-mapping.invalid"));
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
        JSONArray schema = parseJsonArray(inputSchemaText, "workflow.definition.start-form.json.invalid");
        Map<String, Object> input = variables == null ? Collections.<String, Object>emptyMap() : variables;
        Set<String> names = new LinkedHashSet<String>();
        for (Object value : schema) {
            if (!(value instanceof JSONObject)) throw new ServerException(422, I18nUtils.getMessage("workflow.definition.start-form.fields.invalid"));
            JSONObject field = (JSONObject) value;
            String name = field.getString("name");
            if (StringUtils.isBlank(name) || !VARIABLE_NAME.matcher(name).matches())
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.start-form.variable-name.invalid", new Object[]{name}));
            names.add(name);
            Object inputValue = input.get(name);
            if (field.getBooleanValue("required") && (inputValue == null || StringUtils.isBlank(String.valueOf(inputValue)))) {
                String label = StringUtils.trimToNull(field.getString("label"));
                String displayName = label == null ? name : label + "（" + name + "）";
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.start-form.required-field.missing", new Object[]{displayName}));
            }
        }
        for (String name : input.keySet()) {
            if (!names.contains(name)) throw new ServerException(422, I18nUtils.getMessage("workflow.definition.start-form.variable.not-declared", new Object[]{name}));
        }
    }

    /**
 * 新增Variable。
 */
private static void addVariable(Set<String> variables, String name, String errorPrefix) {
        if (StringUtils.isBlank(name)) return;
        if (!VARIABLE_NAME.matcher(name).matches()) throw new ServerException(422, I18nUtils.getMessage(errorPrefix, new Object[]{name}));
        variables.add(name);
    }

    /**
 * 校验References。
 */
    private static void validateReferences(String template, Set<String> available, String nodeId) {
        if (StringUtils.isBlank(template)) return;
        Matcher matcher = VARIABLE_REFERENCE.matcher(template);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!available.contains(name))
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.node.variable.undeclared", new Object[]{nodeId, name}));
        }
    }

    /** 返回变量路径的根变量；支持 order.total 与 $.order.total 两种安全路径写法。 */
    private static String sourceRoot(String source) {
        if (StringUtils.isBlank(source)) return source;
        String value = source.trim();
        if (value.startsWith("$.")) value = value.substring(2);
        int dot = value.indexOf('.');
        return dot < 0 ? value : value.substring(0, dot);
    }

    /** 返回按执行拓扑排序的节点列表（供执行引擎顺序遍历使用）。 */
    public static List<JSONObject> orderedNodes(String nodesText, String edgesText) {
        JSONArray nodes = parseJsonArray(nodesText, "workflow.definition.canvas.json.invalid");
        JSONArray edges = parseJsonArray(edgesText, "workflow.definition.edges.json.invalid");
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
        JSONArray edges = parseJsonArray(edgesText, "workflow.definition.edges.json.invalid");
        Map<String, List<JSONObject>> adj = new LinkedHashMap<String, List<JSONObject>>();
        for (Object value : edges) {
            JSONObject edge = (JSONObject) value;
            adj.computeIfAbsent(edge.getString("source"), k -> new ArrayList<JSONObject>()).add(edge);
        }
        return adj;
    }

    // ── 内部方法 ─────────────────────────────────────────────

    /**
 * 解析JsonArray。
 */
private static JSONArray parseJsonArray(String text, String errorCode) {
        if (StringUtils.isBlank(text)) return new JSONArray();
        try { return JSONArray.parseArray(text); } catch (Exception e) { throw new ServerException(422, I18nUtils.getMessage(errorCode)); }
    }

    /**
 * 构建NodeMap。
 */
private static Map<String, JSONObject> buildNodeMap(JSONArray nodes) {
        Map<String, JSONObject> map = new LinkedHashMap<String, JSONObject>();
        for (Object value : nodes) {
            JSONObject node = (JSONObject) value;
            String id = node.getString("id"), type = node.getString("type");
            if (StringUtils.isBlank(id) || !TYPES.contains(type) || map.put(id, node) != null)
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.node.id-or-type.invalid"));
        }
        return map;
    }

    /**
 * 查找StartId。
 */
private static String findStartId(Map<String, JSONObject> nodeMap) {
        for (Map.Entry<String, JSONObject> e : nodeMap.entrySet()) if ("start".equals(e.getValue().getString("type"))) return e.getKey();
        throw new ServerException(422, I18nUtils.getMessage("workflow.definition.start-node.missing"));
    }

    /**
 * 查找EndId。
 */
private static String findEndId(Map<String, JSONObject> nodeMap) {
        for (Map.Entry<String, JSONObject> e : nodeMap.entrySet()) if ("end".equals(e.getValue().getString("type"))) return e.getKey();
        throw new ServerException(422, I18nUtils.getMessage("workflow.definition.end-node.missing"));
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
