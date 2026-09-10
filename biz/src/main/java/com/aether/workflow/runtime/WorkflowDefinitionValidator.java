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
            "start", "agent", "tool", "interaction", "rule", "http", "notification", "subflow", "parallel", "join", "wait_event", "delay", "end"));
    /** 会把输出写入全局变量的节点类型（outputs 仅在这些节点上合法）。wait_event 仅在收到事件时应用 outputs，超时分支不应用。 */
    private static final Set<String> PRODUCING_TYPES = new HashSet<String>(Arrays.asList(
            "agent", "tool", "interaction", "rule", "http", "notification", "subflow", "wait_event"));
    private static boolean isProducing(String type) {
        return type != null && PRODUCING_TYPES.contains(type);
    }
    private static final Pattern VARIABLE_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    /** 结构化输出目标：可为 a.b.c 这类层级路径（每段均为变量名，不支持数组下标写入）。 */
    private static final Pattern VARIABLE_PATH = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*");
    private static boolean validWriteTarget(String target) {
        return target != null && VARIABLE_PATH.matcher(target).matches();
    }
    /** 匹配 ${a.b.0.c} 形式的变量引用；校验时仅按根段判定数据流可达性。 */
    private static final Pattern VARIABLE_REFERENCE = WorkflowPathResolver.REFERENCE;
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
                if (branches != null) {
                    for (Object branch : branches) {
                        if (!(branch instanceof String) || StringUtils.isBlank(String.valueOf(branch)))
                            throw new ServerException(422, "并行分支入口必须是节点 ID");
                    }
                }
                if (node.containsKey("maxBranches") && (node.getIntValue("maxBranches") <= 0 || node.getIntValue("maxBranches") > 50))
                    throw new ServerException(422, "并行节点 maxBranches 必须在 1 到 50 之间");
                if (node.containsKey("branchTimeoutMillis") && node.getLongValue("branchTimeoutMillis") <= 0)
                    throw new ServerException(422, "并行节点 branchTimeoutMillis 必须大于 0");
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
            if ("interaction".equals(type)) {
                String mode = StringUtils.defaultIfBlank(node.getString("mode"), "form");
                if (!"form".equals(mode) && !"approval".equals(mode))
                    throw new ServerException(422, "交互节点 mode 仅支持 form/approval：" + node.getString("id"));
                if ("form".equals(mode) && StringUtils.isBlank(node.getString("question"))
                        && (node.getJSONArray("questions") == null || node.getJSONArray("questions").isEmpty()))
                    throw new ServerException(422, "表单交互节点必须配置问题说明或问题列表：" + node.getString("id"));
                if ("approval".equals(mode) && StringUtils.isNotBlank(node.getString("approvalMode"))
                        && !"ANY".equals(node.getString("approvalMode")))
                    throw new ServerException(422, "当前审批交互节点仅支持 ANY 审批模式");
            }
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

        // 并行节点：分支优先采用“从并行节点连出的边”的目标（连线即分支）；兼容 legacy branches 节点列表。
        // 校验：分支可达汇聚节点、分支内容仅确定性节点、分支内不嵌套并行。
        for (JSONObject parallel : nodeMap.values()) {
            if (!"parallel".equals(parallel.getString("type"))) continue;
            String parallelId = parallel.getString("id");
            List<JSONObject> outgoing = outEdges.getOrDefault(parallelId, Collections.<JSONObject>emptyList());
            JSONArray branches = parallel.getJSONArray("branches");
            boolean hasLegacyBranches = branches != null && !branches.isEmpty();
            boolean edgeDriven = !outgoing.isEmpty();
            List<String> entries = resolveParallelEntries(parallel, outEdges);
            if (!edgeDriven && !hasLegacyBranches)
                throw new ServerException(422, "并行节点必须引出至少两条分支边或配置 branches：" + parallelId);
            if (edgeDriven && entries.size() < 2)
                throw new ServerException(422, "并行分叉至少需要两条分支：" + parallelId);
            for (String entry : entries) {
                JSONObject entryNode = nodeMap.get(entry);
                if (entryNode == null) throw new ServerException(422, "并行分支入口不存在：" + entry);
                if ("join".equals(entryNode.getString("type")) || "parallel".equals(entryNode.getString("type")))
                    throw new ServerException(422, "并行分支入口不能是汇聚或并行节点：" + entry);
            }
            // 每条分支：走到首个 join 为止收集即时汇聚点，edge-driven 形态同时校验分支内容确定性。
            Set<String> commonJoins = null;
            for (String entry : entries) {
                Set<String> branchJoins = new LinkedHashSet<String>();
                Set<String> visited = new LinkedHashSet<String>();
                Deque<String> stack = new ArrayDeque<String>();
                stack.push(entry);
                while (!stack.isEmpty()) {
                    String current = stack.pop();
                    if (!visited.add(current)) continue;
                    JSONObject currentDef = nodeMap.get(current);
                    if (currentDef == null) continue;
                    if ("join".equals(currentDef.getString("type"))) { branchJoins.add(current); continue; }
                    if (edgeDriven && "parallel".equals(currentDef.getString("type")))
                        throw new ServerException(422, "并行分支内暂不支持嵌套并行节点：" + current);
                    if (edgeDriven && !isDeterministicBranchNode(currentDef))
                        throw new ServerException(422, "并行分支仅支持普通 Agent 与确定性节点（规则/HTTP/通知/延时/自动放行工具），不支持："
                                + current + "（" + currentDef.getString("type") + "）");
                    for (JSONObject edge : outEdges.getOrDefault(current, Collections.<JSONObject>emptyList()))
                        stack.push(edge.getString("target"));
                }
                if (commonJoins == null) commonJoins = new LinkedHashSet<String>(branchJoins);
                else commonJoins.retainAll(branchJoins);
            }
            String configuredJoin = parallel.getString("joinNodeId");
            if (StringUtils.isNotBlank(configuredJoin)) {
                JSONObject join = nodeMap.get(configuredJoin);
                if (join == null || !"join".equals(join.getString("type")))
                    throw new ServerException(422, "并行节点 joinNodeId 必须指向汇聚节点：" + configuredJoin);
                continue; // 显式汇聚，沿用 legacy 宽松行为。
            }
            if (commonJoins == null || commonJoins.isEmpty())
                throw new ServerException(422, "并行节点必须配置 joinNodeId 或让所有分支汇聚到同一 join");
            if (commonJoins.size() > 1)
                throw new ServerException(422, "并行分叉可能汇聚到多个 join，请让所有分支直连同一汇聚节点：" + parallelId);
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

    /** 解析并行节点分支入口：优先“从并行节点连出的边”的目标（编排式，连线即分支）；否则回退 legacy branches 节点列表。 */
    private static List<String> resolveParallelEntries(JSONObject parallel, Map<String, List<JSONObject>> outEdges) {
        List<String> entries = new ArrayList<String>();
        List<JSONObject> outs = outEdges == null ? null : outEdges.get(parallel.getString("id"));
        if (outs != null && !outs.isEmpty()) {
            for (JSONObject edge : outs) {
                String target = edge.getString("target");
                if (!entries.contains(target)) entries.add(target);
            }
            return entries;
        }
        JSONArray branches = parallel.getJSONArray("branches");
        if (branches != null) for (Object branch : branches) {
            String id = String.valueOf(branch);
            if (!entries.contains(id)) entries.add(id);
        }
        return entries;
    }

    /** 并行分支内容允许普通 Agent 或确定性同步节点；工具仅“自动放行(never)”策略可入分支；交互节点/子流程/等待仍不允许。 */
    private static boolean isDeterministicBranchNode(JSONObject node) {
        if (node == null) return false;
        String type = node.getString("type");
        if ("tool".equals(type))
            return "never".equalsIgnoreCase(StringUtils.defaultIfBlank(node.getString("toolApprovalPolicy"), "ask"));
        return "agent".equals(type) || "rule".equals(type) || "http".equals(type)
                || "notification".equals(type) || "delay".equals(type);
    }

    /** 由边数组构建 source → 出边列表。 */
    private static Map<String, List<JSONObject>> buildOutEdgeMap(JSONArray edges) {
        Map<String, List<JSONObject>> map = new LinkedHashMap<String, List<JSONObject>>();
        if (edges == null) return map;
        for (Object value : edges) {
            if (!(value instanceof JSONObject)) continue;
            JSONObject edge = (JSONObject) value;
            map.computeIfAbsent(edge.getString("source"), k -> new ArrayList<JSONObject>()).add(edge);
        }
        return map;
    }

    /**
     * 校验启动表单及节点内变量引用。结构校验与变量契约分开保留。
     * 节点 outputs 的目标变量均视为流程可用变量；引用不存在的变量（含 ${a.b} 的根 a）将拒绝发布，
     * 从而避免运行到一半才发现提示词或工具参数中的拼写错误。
     */
    public static void validateVariables(String nodesText, String edgesText, String inputSchemaText) {
        JSONArray nodes = parseJsonArray(nodesText, "workflow.definition.canvas.json.invalid");
        JSONArray edges = parseJsonArray(edgesText, "workflow.definition.edges.json.invalid");
        JSONArray schema = parseJsonArray(inputSchemaText, "workflow.definition.start-form.json.invalid");
        Map<String, List<JSONObject>> outEdges = buildOutEdgeMap(edges);
        Set<String> declared = new LinkedHashSet<String>();
        for (Object value : schema) {
            if (!(value instanceof JSONObject)) throw new ServerException(422, I18nUtils.getMessage("workflow.definition.start-form.fields.invalid"));
            String name = ((JSONObject) value).getString("name");
            if (StringUtils.isBlank(name) || !VARIABLE_NAME.matcher(name).matches())
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.start-form.variable-name.invalid", new Object[]{name}));
            if (!declared.add(name)) throw new ServerException(422, I18nUtils.getMessage("workflow.definition.start-form.variable-name.duplicate", new Object[]{name}));
        }
        Map<String, Set<String>> availableBefore = availableVariablesBefore(nodes, edges, declared);
        // 各节点将产出的根变量（仅产出类型节点的 outputs target 根段），供同节点行内级联与连线条件校验使用。
        Map<String, Set<String>> nodeProduced = new LinkedHashMap<String, Set<String>>();
        // 使用所有入边均能提供的变量做校验，避免引用后续或另一分支才产生的输出。
        for (Object value : nodes) {
            JSONObject node = (JSONObject) value;
            String nodeId = node.getString("id");
            String nodeType = node.getString("type");
            // 行内运行集：从"节点执行前可用集"出发，随 outputs 逐行推进，让后续行可引用本节点更早行写入的变量（与运行时行序一致）。
            Set<String> running = new LinkedHashSet<String>(availableBefore.get(nodeId));
            Set<String> produced = new LinkedHashSet<String>();

            validateReferences(node.getString("prompt"), running, nodeId);
            validateReferences(node.getString("argumentsTemplate"), running, nodeId);
            validateReferences(node.getString("question"), running, nodeId);
            validateReferences(node.getString("url"), running, nodeId);
            validateReferences(node.getString("bodyTemplate"), running, nodeId);
            validateReferences(node.getString("toTemplate"), running, nodeId);
            validateReferences(node.getString("subjectTemplate"), running, nodeId);
            JSONArray subflowInputMappings = node.getJSONArray("inputMappings");
            if (subflowInputMappings != null) for (Object mappingValue : subflowInputMappings) {
                if (!(mappingValue instanceof JSONObject)) throw new ServerException(422, "子流程输入映射必须是对象数组");
                JSONObject mapping = (JSONObject) mappingValue;
                String target = mapping.getString("target");
                if (StringUtils.isBlank(target) || !VARIABLE_NAME.matcher(target).matches())
                    throw new ServerException(422, "子流程输入目标变量名不合法：" + target);
                validateReferences(mapping.getString("template"), running, nodeId);
                String inputSourceRoot = sourceRoot(mapping.getString("source"));
                if (StringUtils.isNotBlank(inputSourceRoot) && !inputSourceRoot.startsWith("_") && !running.contains(inputSourceRoot))
                    throw new ServerException(422, I18nUtils.getMessage("workflow.variable.not-provided", new Object[]{inputSourceRoot}));
            }
            if ("parallel".equals(nodeType)) {
                for (String branchId : resolveParallelEntries(node, outEdges)) {
                    JSONObject branchNode = null;
                    for (Object candidate : nodes) if (branchId.equals(((JSONObject) candidate).getString("id"))) { branchNode = (JSONObject) candidate; break; }
                    if (branchNode == null) throw new ServerException(422, "并行分支入口不存在：" + branchId);
                    if (!isDeterministicBranchNode(branchNode))
                        throw new ServerException(422, "并行分支仅支持普通 Agent 与确定性节点，不支持交互节点、子流程或等待节点：" + branchId + "（" + branchNode.getString("type") + "）");
                }
            }
            validateReferences(node.getString("idempotencyKeyTemplate"), running, nodeId);
            validateReferences(node.getString("correlationKeyTemplate"), running, nodeId);

            JSONArray outputs = node.getJSONArray("outputs");
            if (outputs != null && !outputs.isEmpty() && !isProducing(nodeType))
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.node.non-producing-outputs", new Object[]{nodeId, nodeType}));
            if (outputs != null) for (Object outputValue : outputs) {
                if (!(outputValue instanceof JSONObject))
                    throw new ServerException(422, "节点 outputs 必须是对象数组：" + nodeId);
                JSONObject mapping = (JSONObject) outputValue;
                String target = mapping.getString("target");
                if (!validWriteTarget(target))
                    throw new ServerException(422, I18nUtils.getMessage("workflow.definition.node.output-variable-name.invalid", new Object[]{target}));
                validateReferences(mapping.getString("template"), running, nodeId);
                String outputSourceRoot = outputSourceRoot(mapping.getString("source"));
                if (StringUtils.isNotBlank(outputSourceRoot) && !outputSourceRoot.startsWith("_") && !running.contains(outputSourceRoot))
                    throw new ServerException(422, I18nUtils.getMessage("workflow.variable.not-provided", new Object[]{outputSourceRoot}));
                // 本行通过后，其写入目标对本节点后续行可见；对下游节点始终可见（availableVariablesBefore 已登记全量）。
                String writeRoot = sourceRoot(target);
                if (StringUtils.isNotBlank(writeRoot) && produced.add(writeRoot)) running.add(writeRoot);
            }
            nodeProduced.put(nodeId, produced);
        }

        // 连线条件在源节点完成后求值（此时源节点 outputs 已全部写入），故按"源节点执行前可用集 ∪ 源节点产出"校验。
        for (String sourceId : outEdges.keySet()) {
            Set<String> base = availableBefore.get(sourceId);
            if (base == null) continue; // 防御：边引用了节点列表外的源（正常发布路径 validate() 已拒绝）
            Set<String> after = new LinkedHashSet<String>(base);
            Set<String> produced = nodeProduced.get(sourceId);
            if (produced != null) after.addAll(produced);
            for (JSONObject edge : outEdges.get(sourceId))
                validateReferences(edge.getString("condition"), after, sourceId);
        }
        // 规则节点分支条件在规则执行前按当前变量求值，等价于该节点执行前的可用集。
        for (Object value : nodes) {
            JSONObject node = (JSONObject) value;
            if (!"rule".equals(node.getString("type"))) continue;
            JSONArray rules = node.getJSONArray("rules");
            if (rules == null) continue;
            for (Object ruleValue : rules) {
                if (!(ruleValue instanceof JSONObject)) continue;
                JSONObject rule = (JSONObject) ruleValue;
                validateReferences(rule.getString("condition"), availableBefore.get(node.getString("id")), node.getString("id"));
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
            // 仅产出类型节点把 outputs target 登记为流程可用变量；start/end/parallel/join/delay 等不产出。
            JSONArray outputs = isProducing(node.getString("type")) ? node.getJSONArray("outputs") : null;
            if (outputs != null) for (Object outputValue : outputs) {
                if (outputValue instanceof JSONObject) {
                    String target = ((JSONObject) outputValue).getString("target");
                    // 结构化目标（如 result.order.total）以其根变量声明可用，供下游 ${result...} 引用判定。
                    addVariable(nodeProduced, target == null ? null : sourceRoot(target), "workflow.definition.node.output-variable-name.invalid");
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
            String path = matcher.group(1);
            String root = sourceRoot(path);
            // `_` 前缀为运行期内部变量（如循环计数 _loop_<edge>_count 由运行时临时写入），校验器无法静态预知，放宽不误报。
            if (root.startsWith("_")) continue;
            if (!available.contains(root))
                throw new ServerException(422, I18nUtils.getMessage("workflow.definition.node.variable.undeclared", new Object[]{nodeId, path}));
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

    /** 输出映射 source 的根：$output/$output.<path> 指向节点自身输出（执行时恒可达），其余视为流程变量路径。 */
    private static String outputSourceRoot(String source) {
        if (StringUtils.isBlank(source)) return null;
        String value = source.trim();
        if ("$output".equals(value) || value.startsWith("$output.")) return null;
        return sourceRoot(value);
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
