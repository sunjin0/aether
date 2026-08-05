# 历史对话性能优化方案

## 方案对比总览

| 方案 | 实施难度 | 性能提升 | Token 节省 | 适用场景 |
|------|---------|---------|-----------|---------|
| **方案一：Redis 缓存** | ⭐ 简单 | ⭐⭐⭐ 高 | 0% | 高并发、频繁读取同一会话 |
| **方案二：消息摘要** | ⭐⭐ 中等 | ⭐⭐ 中 | 40-60% | 长对话、成本敏感 |
| **方案三：滑动窗口 + 压缩** | ⭐⭐⭐ 复杂 | ⭐⭐⭐ 高 | 30-50% | 超长对话、极致优化 |

---

## 方案一：Redis 上下文缓存（推荐优先实施）

### 核心思路
将构建好的 `context` 序列化后缓存到 Redis，避免每次请求都查询数据库。

### 实施步骤

#### 1. 缓存键设计
```
key: agent:context:{conversationId}
value: JSON 序列化的 List<ModelChatMessage>
TTL: 30 分钟（会话活跃期间）
```

#### 2. 缓存策略
- **写入时机**：
  - 首次构建 context 后写入缓存
  - 每次新消息保存后更新缓存（追加新消息）

- **读取时机**：
  - 调用 `buildContext` 时优先从缓存读取
  - 缓存未命中时查询数据库并回填缓存

#### 3. 伪代码实现
```java
private List<ModelChatMessage> buildContext(AgentDefinition agent, String conversationId) {
    // 1. 尝试从缓存读取
    String cacheKey = "agent:context:" + conversationId;
    String cachedContext = redisTemplate.opsForValue().get(cacheKey);
    if (StringUtils.isNotBlank(cachedContext)) {
        return JSON.parseArray(cachedContext, ModelChatMessage.class);
    }

    // 2. 缓存未命中，从数据库构建
    List<ModelChatMessage> context = buildContextFromDb(agent, conversationId);

    // 3. 写入缓存（30分钟过期）
    redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(context), 30, TimeUnit.MINUTES);

    return context;
}

// 新增消息后更新缓存
private void updateContextCache(String conversationId, ModelChatMessage newMessage) {
    String cacheKey = "agent:context:" + conversationId;
    String cachedContext = redisTemplate.opsForValue().get(cacheKey);
    if (StringUtils.isNotBlank(cachedContext)) {
        List<ModelChatMessage> context = JSON.parseArray(cachedContext, ModelChatMessage.class);
        context.add(newMessage);

        // 保持最多20条历史消息
        if (context.size() > 21) { // 1 system + 20 messages
            context = context.subList(context.size() - 21, context.size());
        }

        redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(context), 30, TimeUnit.MINUTES);
    }
}
```

#### 4. 性能收益
- **数据库查询减少**：90%+ 的请求无需查询 `agent_message` 表
- **响应时间降低**：从 50-100ms（数据库查询）降至 5-10ms（Redis 读取）
- **适用场景**：用户连续对话、高并发场景

#### 5. 注意事项
- **缓存一致性**：消息保存后必须更新缓存，或使用延迟双删策略
- **内存占用**：单个会话 context 约 5-20KB，1000 个活跃会话约 5-20MB
- **分布式部署**：多实例部署时 Redis 天然支持共享缓存

---

## 方案二：消息摘要压缩

### 核心思路
将早期历史消息通过模型摘要成简短文本，保留核心语义，减少 token 消耗。

### 实施步骤

#### 1. 分层上下文结构
```
[系统提示词]          ← 始终完整保留
[摘要消息] (1-2条)    ← 早期对话的 AI 摘要
[原始消息] (最近5条)  ← 完整保留最近对话
[当前消息]            ← 用户最新输入
```

#### 2. 触发条件
当对话超过 10 轮时，触发摘要流程：
- 将前 N 条消息发送给模型生成摘要
- 用摘要替换原始消息

#### 3. 摘要 Prompt 示例
```java
String summarizePrompt = "请将以下对话历史总结为关键要点，保留重要信息和用户意图，200字以内：\n\n" + oldMessages;
```

#### 4. 伪代码实现
```java
private List<ModelChatMessage> buildContextWithSummary(AgentDefinition agent, String conversationId) {
    List<ModelChatMessage> context = new ArrayList<>();

    // 1. 添加 system prompt
    if (StringUtils.isNotBlank(agent.getSystemPrompt())) {
        context.add(new ModelChatMessage("system", agent.getSystemPrompt()));
    }

    // 2. 查询所有历史消息
    List<AgentMessage> allMessages = queryAllMessages(conversationId);

    if (allMessages.size() <= 10) {
        // 消息较少，直接返回
        for (AgentMessage msg : allMessages) {
            context.add(new ModelChatMessage(msg.getRole(), msg.getContent()));
        }
    } else {
        // 3. 消息较多，使用摘要
        List<AgentMessage> oldMessages = allMessages.subList(0, allMessages.size() - 5);
        List<AgentMessage> recentMessages = allMessages.subList(allMessages.size() - 5, allMessages.size());

        // 4. 获取或生成摘要
        String summary = getOrCreateSummary(conversationId, oldMessages);
        context.add(new ModelChatMessage("system", "对话历史摘要：" + summary));

        // 5. 添加最近消息
        for (AgentMessage msg : recentMessages) {
            context.add(new ModelChatMessage(msg.getRole(), msg.getContent()));
        }
    }

    return context;
}

private String getOrCreateSummary(String conversationId, List<AgentMessage> oldMessages) {
    // 尝试从缓存读取
    String cacheKey = "agent:summary:" + conversationId;
    String cached = redisTemplate.opsForValue().get(cacheKey);
    if (StringUtils.isNotBlank(cached)) {
        return cached;
    }

    // 调用模型生成摘要
    String summary = callModelToSummarize(oldMessages);

    // 缓存摘要（长期有效）
    redisTemplate.opsForValue().set(cacheKey, summary, 24, TimeUnit.HOURS);

    return summary;
}
```

#### 5. 性能收益
- **Token 节省**：40-60%（早期对话从完整文本变为简短摘要）
- **成本降低**：每次调用减少 30-50% 的 prompt tokens
- **适用场景**：长对话、成本敏感的应用

#### 6. 注意事项
- **摘要成本**：生成摘要本身需要调用一次模型（约 1-2 元/次）
- **信息损失**：摘要可能丢失细节，影响模型回答精度
- **异步生成**：建议后台异步生成摘要，避免阻塞用户请求

---

## 方案三：滑动窗口 + 智能压缩

### 核心思路
结合滑动窗口和消息压缩技术，自动管理上下文长度。

### 核心策略

#### 1. 滑动窗口机制
```
固定保留：System Prompt + 最近 5 条消息
动态窗口：中间消息根据 token 预算动态保留
```

#### 2. 消息优先级评分
为每条消息计算重要性分数：
- 包含用户明确指令：+10 分
- 包含工具调用结果：+8 分
- Assistant 回复长度 > 200 字：+5 分
- 最近 3 条消息：+3 分
- 普通对话：+1 分

按分数降序保留高优先级消息。

#### 3. 伪代码实现
```java
private List<ModelChatMessage> buildContextWithSlidingWindow(
    AgentDefinition agent,
    String conversationId,
    int maxTokens  // 模型最大上下文限制，如 4000
) {
    List<ModelChatMessage> context = new ArrayList<>();

    // 1. 始终保留 system prompt
    if (StringUtils.isNotBlank(agent.getSystemPrompt())) {
        context.add(new ModelChatMessage("system", agent.getSystemPrompt()));
    }
    
    // 2. 查询历史消息
    List<AgentMessage> allMessages = queryAllMessages(conversationId);
    if (allMessages.isEmpty()) {
        return context;
    }
    
    // 3. 强制保留最近 5 条消息
    int keepRecent = Math.min(5, allMessages.size());
    List<AgentMessage> recentMessages = allMessages.subList(
        allMessages.size() - keepRecent,
        allMessages.size()
    );

    // 4. 计算已用 token
    int usedTokens = estimateTokens(context) + estimateTokens(recentMessages);
    int remainingTokens = maxTokens - usedTokens;
    
    // 5. 如果还有剩余空间，按优先级选择中间消息
    if (remainingTokens > 0 && allMessages.size() > keepRecent) {
        List<AgentMessage> middleMessages = allMessages.subList(
            0,
            allMessages.size() - keepRecent
        );

        // 按优先级排序
        List<AgentMessage> prioritized = middleMessages.stream()
            .sorted(Comparator.comparingInt(this::calculatePriority).reversed())
            .collect(Collectors.toList());

        // 选择高优先级消息直到 token 用完
        for (AgentMessage msg : prioritized) {
            int msgTokens = estimateTokens(msg.getContent());
            if (msgTokens <= remainingTokens) {
                context.add(new ModelChatMessage(msg.getRole(), msg.getContent()));
                remainingTokens -= msgTokens;
            }
        }
    }

    // 6. 按时间顺序重组 context
    Collections.sort(context, Comparator.comparingInt(this::getMessageTimeOrder));
    context.addAll(recentMessages);

    return context;
}

private int calculatePriority(AgentMessage msg) {
    int score = 0;
    String content = msg.getContent();

    // 包含指令关键词
    if (content.contains("请") || content.contains("帮我") || content.contains("执行")) {
        score += 10;
    }

    // 工具调用结果
    if (content.contains("tool_result") || content.contains("function_output")) {
        score += 8;
    }

    // 长回复（可能包含重要信息）
    if (content.length() > 200) {
        score += 5;
    }
    
    return score;
}
```

#### 4. 性能收益
- **Token 节省**：30-50%（智能筛选高价值消息）
- **上下文利用率**：最大化利用模型 token 限制
- **适用场景**：超长对话、需要精细控制成本的场景

#### 5. 注意事项
- **实现复杂度**：需要实现评分算法和排序逻辑
- **维护成本**：需要根据业务调整优先级规则
- **测试要求**：需要大量测试验证摘要质量

---

## 推荐实施路径

### 第一阶段：快速见效（1-2 天）
✅ 实施方案一（Redis 缓存）
- 收益立竿见影，实施简单
- 减少数据库查询 90%+

### 第二阶段：成本优化（3-5 天）
✅ 实施方案二（消息摘要）
- 适用于长对话场景
- Token 成本降低 40-60%

### 第三阶段：极致优化（1-2 周）
✅ 实施方案三（滑动窗口 + 智能压缩）
- 需要充分测试和调整
- 适合大规模生产环境

---

## 监控指标建议

实施优化后，建议监控以下指标：

| 指标 | 目标值 | 监控方式 |
|------|--------|---------|
| 平均响应时间 | < 500ms | APM 监控 |
| 数据库查询次数 | 减少 80%+ | SQL 日志 |
| 平均 Prompt Tokens | 降低 30-50% | API 响应日志 |
| 缓存命中率 | > 85% | Redis 监控 |
| 摘要生成延迟 | < 2s | 异步任务监控 |

---

## 技术栈兼容性

您的项目已具备的技术支持：
- ✅ Redis（`common` 模块已配置）
- ✅ MyBatis-Plus（方便数据库查询优化）
- ✅ 异步线程池（可用于异步摘要生成）
- ✅ ModelClient（可复用调用摘要模型）
