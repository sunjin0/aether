# Admin Preference System Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the admin preference system to support structured preferences with dynamic reasoning, implicit
learning with decay, and user feedback capabilities.

**Architecture:** Two-table design (preference + event log) with a preference reasoning engine that computes effective
scores based on priority, decay, and confidence. Implicit learning extracts preferences from conversations and logs
events. Redis caching for performance.

**Tech Stack:** Java 8, Spring Boot 2.7.18, MyBatis-Plus, MySQL/PostgreSQL, Redis

---

## File Structure

| File                                                                                        | Action  | Responsibility                        |
|---------------------------------------------------------------------------------------------|---------|---------------------------------------|
| `api/src/main/resources/sql/mysql/001-schema.sql`                                           | Modify  | Add new tables, remove old table      |
| `api/src/main/resources/sql/postgresql/001-schema.sql`                                      | Modify  | Add new tables, remove old table      |
| `api/src/main/resources/sql/mysql/002-data.sql`                                             | Modify  | Remove Admin_Preference_Category dict |
| `api/src/main/resources/sql/postgresql/002-data.sql`                                        | Modify  | Remove Admin_Preference_Category dict |
| `api/src/main/java/com/aether/sys/entity/AdminPreference.java`                              | Rewrite | New field mappings                    |
| `api/src/main/java/com/aether/sys/entity/AdminPreferenceEvent.java`                         | Create  | Event log entity                      |
| `api/src/main/java/com/aether/sys/vo/AdminPreferenceVo.java`                                | Rewrite | Match new entity                      |
| `api/src/main/java/com/aether/sys/vo/AdminPreferenceEventVo.java`                           | Create  | Event log VO                          |
| `api/src/main/java/com/aether/sys/mapper/AdminPreferenceMapper.java`                        | Modify  | Add custom queries                    |
| `api/src/main/java/com/aether/sys/mapper/AdminPreferenceEventMapper.java`                   | Create  | Event log mapper                      |
| `api/src/main/java/com/aether/sys/service/AdminPreferenceService.java`                      | Modify  | Add new methods                       |
| `api/src/main/java/com/aether/sys/service/AdminPreferenceEventService.java`                 | Create  | Event log service interface           |
| `biz/src/main/java/com/aether/sys/service/impl/AdminPreferenceServiceImpl.java`             | Rewrite | Reasoning engine + decay              |
| `biz/src/main/java/com/aether/sys/service/impl/AdminPreferenceEventServiceImpl.java`        | Create  | Event log service impl                |
| `biz/src/main/java/com/aether/sys/service/impl/PreferenceReasoningEngine.java`              | Create  | Core reasoning logic                  |
| `biz/src/main/java/com/aether/sys/service/impl/PreferenceDecayScheduler.java`               | Create  | Daily decay scheduler                 |
| `api/src/main/java/com/aether/agent/service/AdminPreferenceExtractionService.java`          | Modify  | Update interface                      |
| `biz/src/main/java/com/aether/agent/service/impl/AdminPreferenceExtractionServiceImpl.java` | Rewrite | New learning logic                    |
| `admin/src/main/java/com/aether/sys/controller/AdminPreferenceController.java`              | Rewrite | Add feedback endpoints                |
| `admin/src/main/java/com/aether/sys/controller/AdminPreferenceEventController.java`         | Create  | Event log controller                  |
| `biz/src/main/java/com/aether/agent/service/KnowledgeContextService.java`                   | Modify  | Use new reasoning engine              |

---

## Task 1: DDL Scripts - Create New Tables

**Files:**

- Modify: `api/src/main/resources/sql/mysql/001-schema.sql`
- Modify: `api/src/main/resources/sql/postgresql/001-schema.sql`

- [ ] **Step 1: Add MySQL DDL for sys_admin_preference**

Append to `api/src/main/resources/sql/mysql/001-schema.sql`:

```sql
-- =====================================================
-- sys_admin_preference (redesigned)
-- =====================================================
CREATE TABLE IF NOT EXISTS `sys_admin_preference` (
    `id`              BIGINT       NOT NULL PRIMARY KEY COMMENT 'Primary key',
    `admin_id`        BIGINT       NOT NULL COMMENT 'User ID',
    `category`        VARCHAR(32)  NOT NULL COMMENT 'language/style/format/tech_stack/tool_strategy',
    `key_name`        VARCHAR(128) NOT NULL COMMENT 'Preference key',
    `value`           VARCHAR(512) NOT NULL COMMENT 'Preference value',
    `description`     VARCHAR(256)          COMMENT 'Human-readable description',
    `priority`        INT          NOT NULL DEFAULT 50 COMMENT 'Priority 0-100',
    `scope`           VARCHAR(32)  NOT NULL DEFAULT 'global' COMMENT 'global/session/task_type',
    `scope_detail`    VARCHAR(64)           COMMENT 'Task type when scope=task_type',
    `source`          VARCHAR(16)  NOT NULL DEFAULT 'explicit' COMMENT 'explicit/implicit',
    `confidence`      DECIMAL(4,2) NOT NULL DEFAULT 1.00 COMMENT 'Confidence score',
    `usage_count`     INT          NOT NULL DEFAULT 0 COMMENT 'Usage count',
    `last_used_at`    BIGINT                COMMENT 'Last used timestamp',
    `expires_at`      BIGINT                COMMENT 'Expiration time, NULL=never',
    `decay_rate`      DECIMAL(4,2) NOT NULL DEFAULT 0.00 COMMENT 'Daily decay rate',
    `effective_score` DECIMAL(6,2) NOT NULL DEFAULT 100.00 COMMENT 'Current effective score',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '0=disabled 1=enabled',
    `created_at`      BIGINT       NOT NULL COMMENT 'Created timestamp',
    `updated_at`      BIGINT       NOT NULL COMMENT 'Updated timestamp',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT 'Deleted flag',
    KEY `idx_admin_id` (`admin_id`),
    KEY `idx_admin_category` (`admin_id`, `category`),
    KEY `idx_admin_key` (`admin_id`, `key_name`),
    KEY `idx_expires` (`expires_at`),
    KEY `idx_effective` (`admin_id`, `effective_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Admin user preferences';

-- =====================================================
-- sys_admin_preference_event
-- =====================================================
CREATE TABLE IF NOT EXISTS `sys_admin_preference_event` (
    `id`               BIGINT       NOT NULL PRIMARY KEY COMMENT 'Primary key',
    `admin_id`         BIGINT       NOT NULL COMMENT 'User ID',
    `preference_id`    BIGINT                COMMENT 'Related preference ID',
    `event_type`       VARCHAR(16)  NOT NULL COMMENT 'extract/confirm/reject/override/use',
    `category`         VARCHAR(32)           COMMENT 'Extracted category',
    `key_name`         VARCHAR(128)          COMMENT 'Extracted key',
    `value`            VARCHAR(512)          COMMENT 'Extracted value',
    `confidence`       DECIMAL(4,2)          COMMENT 'Confidence score',
    `conversation_id`  BIGINT                COMMENT 'Source conversation',
    `message_id`       BIGINT                COMMENT 'Source message',
    `context_snapshot` TEXT                  COMMENT 'Context summary (JSON)',
    `created_at`       BIGINT       NOT NULL COMMENT 'Created timestamp',
    KEY `idx_admin_id` (`admin_id`),
    KEY `idx_admin_event` (`admin_id`, `event_type`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Preference events log';
```

- [ ] **Step 2: Add PostgreSQL DDL for sys_admin_preference**

Append to `api/src/main/resources/sql/postgresql/001-schema.sql`:

```sql
-- =====================================================
-- sys_admin_preference (redesigned)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_admin_preference (
    id              VARCHAR(32)  NOT NULL PRIMARY KEY,
    admin_id        VARCHAR(32)  NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    key_name        VARCHAR(128) NOT NULL,
    value           VARCHAR(512) NOT NULL,
    description     VARCHAR(256),
    priority        INT          NOT NULL DEFAULT 50,
    scope           VARCHAR(32)  NOT NULL DEFAULT 'global',
    scope_detail    VARCHAR(64),
    source          VARCHAR(16)  NOT NULL DEFAULT 'explicit',
    confidence      DECIMAL(4,2) NOT NULL DEFAULT 1.00,
    usage_count     INT          NOT NULL DEFAULT 0,
    last_used_at    BIGINT,
    expires_at      BIGINT,
    decay_rate      DECIMAL(4,2) NOT NULL DEFAULT 0.00,
    effective_score DECIMAL(6,2) NOT NULL DEFAULT 100.00,
    status          SMALLINT     NOT NULL DEFAULT 1,
    created_at      BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_admin_id ON sys_admin_preference(admin_id);
CREATE INDEX idx_admin_category ON sys_admin_preference(admin_id, category);
CREATE INDEX idx_admin_key ON sys_admin_preference(admin_id, key_name);
CREATE INDEX idx_expires ON sys_admin_preference(expires_at);
CREATE INDEX idx_effective ON sys_admin_preference(admin_id, effective_score);

-- =====================================================
-- sys_admin_preference_event
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_admin_preference_event (
    id               VARCHAR(32)  NOT NULL PRIMARY KEY,
    admin_id         VARCHAR(32)  NOT NULL,
    preference_id    VARCHAR(32),
    event_type       VARCHAR(16)  NOT NULL,
    category         VARCHAR(32),
    key_name         VARCHAR(128),
    value            VARCHAR(512),
    confidence       DECIMAL(4,2),
    conversation_id  VARCHAR(32),
    message_id       VARCHAR(32),
    context_snapshot TEXT,
    created_at       BIGINT       NOT NULL
);
CREATE INDEX idx_admin_id ON sys_admin_preference_event(admin_id);
CREATE INDEX idx_admin_event ON sys_admin_preference_event(admin_id, event_type);
CREATE INDEX idx_created ON sys_admin_preference_event(created_at);
```

- [ ] **Step 3: Remove old sys_admin_preference table from MySQL DDL**

Find and remove the old `sys_admin_preference` table definition (lines ~624-643) from
`api/src/main/resources/sql/mysql/001-schema.sql`.

- [ ] **Step 4: Remove old sys_admin_preference table from PostgreSQL DDL**

Find and remove the old `sys_admin_preference` table definition (lines ~294-314) from
`api/src/main/resources/sql/postgresql/001-schema.sql`.

- [ ] **Step 5: Commit DDL changes**

```bash
git add api/src/main/resources/sql/mysql/001-schema.sql api/src/main/resources/sql/postgresql/001-schema.sql
git commit -m "feat(preference): add new DDL for redesigned preference tables"
```

---

## Task 2: DDL Scripts - Remove Dictionary Dependency

**Files:**

- Modify: `api/src/main/resources/sql/mysql/002-data.sql`
- Modify: `api/src/main/resources/sql/postgresql/002-data.sql`

- [ ] **Step 1: Remove Admin_Preference_Category from MySQL seed data**

Find and remove the Admin_Preference_Category dictionary entries (lines ~353-372) from
`api/src/main/resources/sql/mysql/002-data.sql`.

- [ ] **Step 2: Remove Admin_Preference_Category from PostgreSQL seed data**

Find and remove the Admin_Preference_Category dictionary entries (lines ~363-382) from
`api/src/main/resources/sql/postgresql/002-data.sql`.

- [ ] **Step 3: Commit seed data changes**

```bash
git add api/src/main/resources/sql/mysql/002-data.sql api/src/main/resources/sql/postgresql/002-data.sql
git commit -m "refactor(preference): remove Admin_Preference_Category dictionary dependency"
```

---

## Task 3: Entity Layer - AdminPreference

**Files:**

- Rewrite: `api/src/main/java/com/aether/sys/entity/AdminPreference.java`

- [ ] **Step 1: Rewrite AdminPreference entity**

Replace content of `api/src/main/java/com/aether/sys/entity/AdminPreference.java`:

```java
package com.aether.sys.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_admin_preference")
public class AdminPreference extends BaseEntity {

    private String adminId;

    private String category;

    private String keyName;

    private String value;

    private String description;

    private Integer priority;

    private String scope;

    private String scopeDetail;

    private String source;

    private BigDecimal confidence;

    private Integer usageCount;

    private Long lastUsedAt;

    private Long expiresAt;

    private BigDecimal decayRate;

    private BigDecimal effectiveScore;

    private Integer status;

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;

    public static final String SOURCE_EXPLICIT = "explicit";
    public static final String SOURCE_IMPLICIT = "implicit";

    public static final String SCOPE_GLOBAL = "global";
    public static final String SCOPE_SESSION = "session";
    public static final String SCOPE_TASK_TYPE = "task_type";

    public static final String CATEGORY_LANGUAGE = "language";
    public static final String CATEGORY_STYLE = "style";
    public static final String CATEGORY_FORMAT = "format";
    public static final String CATEGORY_TECH_STACK = "tech_stack";
    public static final String CATEGORY_TOOL_STRATEGY = "tool_strategy";
}
```

- [ ] **Step 2: Commit entity changes**

```bash
git add api/src/main/java/com/aether/sys/entity/AdminPreference.java
git commit -m "feat(preference): rewrite AdminPreference entity with new fields"
```

---

## Task 4: Entity Layer - AdminPreferenceEvent

**Files:**

- Create: `api/src/main/java/com/aether/sys/entity/AdminPreferenceEvent.java`

- [ ] **Step 1: Create AdminPreferenceEvent entity**

Create `api/src/main/java/com/aether/sys/entity/AdminPreferenceEvent.java`:

```java
package com.aether.sys.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_admin_preference_event")
public class AdminPreferenceEvent extends BaseEntity {

    private String adminId;

    private String preferenceId;

    private String eventType;

    private String category;

    private String keyName;

    private String value;

    private java.math.BigDecimal confidence;

    private String conversationId;

    private String messageId;

    private String contextSnapshot;

    public static final String EVENT_EXTRACT = "extract";
    public static final String EVENT_CONFIRM = "confirm";
    public static final String EVENT_REJECT = "reject";
    public static final String EVENT_OVERRIDE = "override";
    public static final String EVENT_USE = "use";
}
```

- [ ] **Step 2: Commit entity changes**

```bash
git add api/src/main/java/com/aether/sys/entity/AdminPreferenceEvent.java
git commit -m "feat(preference): create AdminPreferenceEvent entity"
```

---

## Task 5: VO Layer

**Files:**

- Rewrite: `api/src/main/java/com/aether/sys/vo/AdminPreferenceVo.java`
- Create: `api/src/main/java/com/aether/sys/vo/AdminPreferenceEventVo.java`

- [ ] **Step 1: Rewrite AdminPreferenceVo**

Replace content of `api/src/main/java/com/aether/sys/vo/AdminPreferenceVo.java`:

```java
package com.aether.sys.vo;

import com.aether.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminPreferenceVo extends BaseEntity {

    private String id;

    private String adminId;

    private String category;

    private String keyName;

    private String value;

    private String description;

    private Integer priority;

    private String scope;

    private String scopeDetail;

    private String source;

    private BigDecimal confidence;

    private Integer usageCount;

    private Long lastUsedAt;

    private Long expiresAt;

    private BigDecimal decayRate;

    private BigDecimal effectiveScore;

    private Integer status;

    private Long current;

    private Long pageSize;
}
```

- [ ] **Step 2: Create AdminPreferenceEventVo**

Create `api/src/main/java/com/aether/sys/vo/AdminPreferenceEventVo.java`:

```java
package com.aether.sys.vo;

import com.aether.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminPreferenceEventVo extends BaseEntity {

    private String id;

    private String adminId;

    private String preferenceId;

    private String eventType;

    private String category;

    private String keyName;

    private String value;

    private BigDecimal confidence;

    private String conversationId;

    private String messageId;

    private String contextSnapshot;

    private Long current;

    private Long pageSize;
}
```

- [ ] **Step 3: Commit VO changes**

```bash
git add api/src/main/java/com/aether/sys/vo/AdminPreferenceVo.java api/src/main/java/com/aether/sys/vo/AdminPreferenceEventVo.java
git commit -m "feat(preference): rewrite VOs for new preference structure"
```

---

## Task 6: Mapper Layer

**Files:**

- Modify: `api/src/main/java/com/aether/sys/mapper/AdminPreferenceMapper.java`
- Create: `api/src/main/java/com/aether/sys/mapper/AdminPreferenceEventMapper.java`

- [ ] **Step 1: Update AdminPreferenceMapper**

Replace content of `api/src/main/java/com/aether/sys/mapper/AdminPreferenceMapper.java`:

```java
package com.aether.sys.mapper;

import com.aether.sys.entity.AdminPreference;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AdminPreferenceMapper extends BaseMapper<AdminPreference> {

    @Select("SELECT * FROM sys_admin_preference WHERE admin_id = #{adminId} AND deleted = 0 AND status = 1 ORDER BY effective_score DESC")
    List<AdminPreference> selectEffectivePreferences(@Param("adminId") String adminId);

    @Select("SELECT * FROM sys_admin_preference WHERE admin_id = #{adminId} AND key_name = #{keyName} AND deleted = 0 LIMIT 1")
    AdminPreference selectByKey(@Param("adminId") String adminId, @Param("keyName") String keyName);

    @Select("SELECT COUNT(*) FROM sys_admin_preference WHERE admin_id = #{adminId} AND key_name = #{keyName} AND value = #{value} AND deleted = 0")
    int countDuplicate(@Param("adminId") String adminId, @Param("keyName") String keyName, @Param("value") String value);

    @Select("UPDATE sys_admin_preference SET effective_score = #{score} WHERE id = #{id}")
    int updateEffectiveScore(@Param("id") String id, @Param("score") BigDecimal score);
}
```

- [ ] **Step 2: Create AdminPreferenceEventMapper**

Create `api/src/main/java/com/aether/sys/mapper/AdminPreferenceEventMapper.java`:

```java
package com.aether.sys.mapper;

import com.aether.sys.entity.AdminPreferenceEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminPreferenceEventMapper extends BaseMapper<AdminPreferenceEvent> {
}
```

- [ ] **Step 3: Commit Mapper changes**

```bash
git add api/src/main/java/com/aether/sys/mapper/AdminPreferenceMapper.java api/src/main/java/com/aether/sys/mapper/AdminPreferenceEventMapper.java
git commit -m "feat(preference): add mapper methods for preference queries"
```

---

## Task 7: Service Interface - AdminPreferenceService

**Files:**

- Modify: `api/src/main/java/com/aether/sys/service/AdminPreferenceService.java`

- [ ] **Step 1: Rewrite AdminPreferenceService interface**

Replace content of `api/src/main/java/com/aether/sys/service/AdminPreferenceService.java`:

```java
package com.aether.sys.service;

import com.aether.sys.entity.AdminPreference;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AdminPreferenceService extends IService<AdminPreference> {

    String buildPreferenceContext(String adminId, String taskType);

    AdminPreference getEffectivePreference(String adminId, String keyName, String taskType);

    void incrementUsage(String preferenceId);

    void adjustConfidence(String preferenceId, BigDecimal delta);

    void updateEffectiveScore(String preferenceId);

    List<AdminPreference> listByAdminId(String adminId);

    boolean clearUserCache(String adminId);
}
```

- [ ] **Step 2: Commit service interface changes**

```bash
git add api/src/main/java/com/aether/sys/service/AdminPreferenceService.java
git commit -m "feat(preference): update AdminPreferenceService interface"
```

---

## Task 8: Service Interface - AdminPreferenceEventService

**Files:**

- Create: `api/src/main/java/com/aether/sys/service/AdminPreferenceEventService.java`

- [ ] **Step 1: Create AdminPreferenceEventService interface**

Create `api/src/main/java/com/aether/sys/service/AdminPreferenceEventService.java`:

```java
package com.aether.sys.service;

import com.aether.sys.entity.AdminPreferenceEvent;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AdminPreferenceEventService extends IService<AdminPreferenceEvent> {

    void logEvent(AdminPreferenceEvent event);
}
```

- [ ] **Step 2: Commit service interface**

```bash
git add api/src/main/java/com/aether/sys/service/AdminPreferenceEventService.java
git commit -m "feat(preference): create AdminPreferenceEventService interface"
```

---

## Task 9: PreferenceReasoningEngine

**Files:**

- Create: `biz/src/main/java/com/aether/sys/service/impl/PreferenceReasoningEngine.java`

- [ ] **Step 1: Create PreferenceReasoningEngine**

Create `biz/src/main/java/com/aether/sys/service/impl/PreferenceReasoningEngine.java`:

```java
package com.aether.sys.service.impl;

import com.aether.sys.entity.AdminPreference;
import com.aether.sys.mapper.AdminPreferenceMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class PreferenceReasoningEngine {

    private static final String CACHE_PREFIX = "pref:ctx:";
    private static final long CACHE_TTL_MINUTES = 5;
    private static final int MAX_PROMPT_LENGTH = 2000;
    private static final long MILLIS_PER_DAY = 24 * 3600 * 1000L;

    @Autowired
    private AdminPreferenceMapper preferenceMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public List<AdminPreference> resolveEffectivePreferences(String adminId, String taskType) {
        String cacheKey = CACHE_PREFIX + adminId + ":" + (taskType != null ? taskType : "default");
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return deserializePreferences(cached);
        }

        List<AdminPreference> allPreferences = preferenceMapper.selectEffectivePreferences(adminId);
        if (allPreferences == null || allPreferences.isEmpty()) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();

        List<AdminPreference> effective = allPreferences.stream()
                .filter(p -> !isExpired(p, now))
                .filter(p -> matchesScope(p, taskType))
                .collect(Collectors.toList());

        Map<String, AdminPreference> bestByKey = new LinkedHashMap<>();
        for (AdminPreference pref : effective) {
            BigDecimal score = calculateEffectiveScore(pref, now);
            pref.setEffectiveScore(score);

            String key = pref.getKeyName();
            AdminPreference existing = bestByKey.get(key);
            if (existing == null || score.compareTo(existing.getEffectiveScore()) > 0) {
                bestByKey.put(key, pref);
            }
        }

        List<AdminPreference> result = bestByKey.values().stream()
                .sorted(Comparator.comparing(AdminPreference::getEffectiveScore).reversed())
                .collect(Collectors.toList());

        redisTemplate.opsForValue().set(cacheKey, serializePreferences(result), CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        return result;
    }

    public String buildPreferenceContext(String adminId, String taskType) {
        List<AdminPreference> effective = resolveEffectivePreferences(adminId, taskType);
        if (effective.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder("【User Preferences (sorted by priority)】\n");
        for (AdminPreference pref : effective) {
            if (StringUtils.isBlank(pref.getValue())) {
                continue;
            }
            builder.append("- [").append(pref.getCategory()).append("] ");
            builder.append(pref.getValue());
            if (StringUtils.isNotBlank(pref.getScopeDetail())) {
                builder.append(" (scope: ").append(pref.getScope()).append(":").append(pref.getScopeDetail()).append(")");
            } else {
                builder.append(" (scope: ").append(pref.getScope()).append(")");
            }
            builder.append(", priority: ").append(pref.getEffectiveScore().intValue());
            builder.append('\n');

            if (builder.length() > MAX_PROMPT_LENGTH) {
                break;
            }
        }
        return builder.toString();
    }

    private boolean isExpired(AdminPreference pref, long now) {
        return pref.getExpiresAt() != null && pref.getExpiresAt() < now;
    }

    private boolean matchesScope(AdminPreference pref, String taskType) {
        if (SCOPE_GLOBAL.equals(pref.getScope())) {
            return true;
        }
        if (SCOPE_SESSION.equals(pref.getScope())) {
            return true;
        }
        if (SCOPE_TASK_TYPE.equals(pref.getScope())) {
            return StringUtils.isNotBlank(taskType) && taskType.equals(pref.getScopeDetail());
        }
        return false;
    }

    private BigDecimal calculateEffectiveScore(AdminPreference pref, long now) {
        BigDecimal priority = BigDecimal.valueOf(pref.getPriority() != null ? pref.getPriority() : 50);
        BigDecimal confidence = pref.getConfidence() != null ? pref.getConfidence() : BigDecimal.ONE;
        BigDecimal decayFactor = calculateDecayFactor(pref, now);
        return priority.multiply(decayFactor).multiply(confidence).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDecayFactor(AdminPreference pref, long now) {
        if (pref.getDecayRate() == null || pref.getDecayRate().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        if (pref.getLastUsedAt() == null) {
            return BigDecimal.ONE;
        }
        long daysSinceLastUse = (now - pref.getLastUsedAt()) / MILLIS_PER_DAY;
        double factor = Math.max(0.1, 1.0 - pref.getDecayRate().doubleValue() * daysSinceLastUse);
        return BigDecimal.valueOf(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public void clearUserCache(String adminId) {
        Set<String> keys = redisTemplate.keys(CACHE_PREFIX + adminId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String serializePreferences(List<AdminPreference> prefs) {
        return prefs.stream()
                .map(p -> p.getId() + "|" + p.getKeyName() + "|" + p.getValue() + "|" + p.getEffectiveScore())
                .collect(Collectors.joining(";"));
    }

    private List<AdminPreference> deserializePreferences(String data) {
        if (StringUtils.isBlank(data)) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private static final String SCOPE_GLOBAL = "global";
    private static final String SCOPE_SESSION = "session";
    private static final String SCOPE_TASK_TYPE = "task_type";
}
```

- [ ] **Step 2: Commit PreferenceReasoningEngine**

```bash
git add biz/src/main/java/com/aether/sys/service/impl/PreferenceReasoningEngine.java
git commit -m "feat(preference): create PreferenceReasoningEngine"
```

---

## Task 10: Service Implementation - AdminPreferenceEventServiceImpl

**Files:**

- Create: `biz/src/main/java/com/aether/sys/service/impl/AdminPreferenceEventServiceImpl.java`

- [ ] **Step 1: Create AdminPreferenceEventServiceImpl**

Create `biz/src/main/java/com/aether/sys/service/impl/AdminPreferenceEventServiceImpl.java`:

```java
package com.aether.sys.service.impl;

import com.aether.sys.entity.AdminPreferenceEvent;
import com.aether.sys.mapper.AdminPreferenceEventMapper;
import com.aether.sys.service.AdminPreferenceEventService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AdminPreferenceEventServiceImpl extends ServiceImpl<AdminPreferenceEventMapper, AdminPreferenceEvent>
        implements AdminPreferenceEventService {

    @Override
    public void logEvent(AdminPreferenceEvent event) {
        if (event == null) {
            return;
        }
        save(event);
    }
}
```

- [ ] **Step 2: Commit AdminPreferenceEventServiceImpl**

```bash
git add biz/src/main/java/com/aether/sys/service/impl/AdminPreferenceEventServiceImpl.java
git commit -m "feat(preference): create AdminPreferenceEventServiceImpl"
```

---

## Task 11: Service Implementation - AdminPreferenceServiceImpl

**Files:**

- Rewrite: `biz/src/main/java/com/aether/sys/service/impl/AdminPreferenceServiceImpl.java`

- [ ] **Step 1: Rewrite AdminPreferenceServiceImpl**

Replace content of `biz/src/main/java/com/aether/sys/service/impl/AdminPreferenceServiceImpl.java`:

```java
package com.aether.sys.service.impl;

import com.aether.sys.entity.AdminPreference;
import com.aether.sys.mapper.AdminPreferenceMapper;
import com.aether.sys.service.AdminPreferenceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AdminPreferenceServiceImpl extends ServiceImpl<AdminPreferenceMapper, AdminPreference>
        implements AdminPreferenceService {

    @Autowired
    private PreferenceReasoningEngine reasoningEngine;

    @Override
    public String buildPreferenceContext(String adminId, String taskType) {
        return reasoningEngine.buildPreferenceContext(adminId, taskType);
    }

    @Override
    public AdminPreference getEffectivePreference(String adminId, String keyName, String taskType) {
        List<AdminPreference> effective = reasoningEngine.resolveEffectivePreferences(adminId, taskType);
        return effective.stream()
                .filter(p -> p.getKeyName().equals(keyName))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void incrementUsage(String preferenceId) {
        AdminPreference pref = getById(preferenceId);
        if (pref != null) {
            pref.setUsageCount(pref.getUsageCount() + 1);
            pref.setLastUsedAt(System.currentTimeMillis());
            updateById(pref);
            reasoningEngine.clearUserCache(pref.getAdminId());
        }
    }

    @Override
    public void adjustConfidence(String preferenceId, BigDecimal delta) {
        AdminPreference pref = getById(preferenceId);
        if (pref != null) {
            BigDecimal newConfidence = pref.getConfidence().add(delta);
            if (newConfidence.compareTo(BigDecimal.ZERO) < 0) {
                newConfidence = BigDecimal.ZERO;
            }
            if (newConfidence.compareTo(BigDecimal.ONE) > 0) {
                newConfidence = BigDecimal.ONE;
            }
            pref.setConfidence(newConfidence);

            if (newConfidence.compareTo(BigDecimal.valueOf(0.3)) < 0) {
                pref.setStatus(AdminPreference.STATUS_DISABLED);
            }

            updateById(pref);
            reasoningEngine.clearUserCache(pref.getAdminId());
        }
    }

    @Override
    public void updateEffectiveScore(String preferenceId) {
        AdminPreference pref = getById(preferenceId);
        if (pref != null) {
            reasoningEngine.updateEffectiveScore(preferenceId);
            reasoningEngine.clearUserCache(pref.getAdminId());
        }
    }

    @Override
    public List<AdminPreference> listByAdminId(String adminId) {
        return list(Wrappers.lambdaQuery(AdminPreference.class)
                .eq(AdminPreference::getAdminId, adminId)
                .eq(AdminPreference::getDeleted, false)
                .orderByDesc(AdminPreference::getEffectiveScore));
    }

    @Override
    public boolean clearUserCache(String adminId) {
        reasoningEngine.clearUserCache(adminId);
        return true;
    }
}
```

- [ ] **Step 2: Commit AdminPreferenceServiceImpl**

```bash
git add biz/src/main/java/com/aether/sys/service/impl/AdminPreferenceServiceImpl.java
git commit -m "feat(preference): rewrite AdminPreferenceServiceImpl with reasoning engine"
```

---

## Task 12: PreferenceDecayScheduler

**Files:**

- Create: `biz/src/main/java/com/aether/sys/service/impl/PreferenceDecayScheduler.java`

- [ ] **Step 1: Create PreferenceDecayScheduler**

Create `biz/src/main/java/com/aether/sys/service/impl/PreferenceDecayScheduler.java`:

```java
package com.aether.sys.service.impl;

import com.aether.sys.entity.AdminPreference;
import com.aether.sys.mapper.AdminPreferenceMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PreferenceDecayScheduler {

    private static final long MILLIS_PER_DAY = 24 * 3600 * 1000L;
    private static final BigDecimal MIN_EFFECTIVE_SCORE = BigDecimal.valueOf(10);

    @Autowired
    private AdminPreferenceMapper preferenceMapper;

    @Autowired
    private PreferenceReasoningEngine reasoningEngine;

    @Scheduled(cron = "0 0 2 * * ?")
    public void recalculateEffectiveScores() {
        List<AdminPreference> allPreferences = preferenceMapper.selectList(
                Wrappers.lambdaQuery(AdminPreference.class)
                        .eq(AdminPreference::getStatus, AdminPreference.STATUS_ENABLED)
                        .eq(AdminPreference::getDeleted, false));

        long now = System.currentTimeMillis();

        for (AdminPreference pref : allPreferences) {
            BigDecimal newScore = calculateEffectiveScore(pref, now);
            pref.setEffectiveScore(newScore);
            preferenceMapper.updateEffectiveScore(pref.getId(), newScore);
        }

        reasoningEngine.clearUserCache(null);
    }

    private BigDecimal calculateEffectiveScore(AdminPreference pref, long now) {
        BigDecimal priority = BigDecimal.valueOf(pref.getPriority() != null ? pref.getPriority() : 50);
        BigDecimal confidence = pref.getConfidence() != null ? pref.getConfidence() : BigDecimal.ONE;
        BigDecimal decayFactor = calculateDecayFactor(pref, now);
        return priority.multiply(decayFactor).multiply(confidence).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDecayFactor(AdminPreference pref, long now) {
        if (pref.getDecayRate() == null || pref.getDecayRate().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        if (pref.getLastUsedAt() == null) {
            return BigDecimal.ONE;
        }
        long daysSinceLastUse = (now - pref.getLastUsedAt()) / MILLIS_PER_DAY;
        double factor = Math.max(0.1, 1.0 - pref.getDecayRate().doubleValue() * daysSinceLastUse);
        return BigDecimal.valueOf(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 2: Commit PreferenceDecayScheduler**

```bash
git add biz/src/main/java/com/aether/sys/service/impl/PreferenceDecayScheduler.java
git commit -m "feat(preference): create PreferenceDecayScheduler"
```

---

## Task 13: Extraction Service Interface

**Files:**

- Modify: `api/src/main/java/com/aether/agent/service/AdminPreferenceExtractionService.java`

- [ ] **Step 1: Update AdminPreferenceExtractionService interface**

Replace content of `api/src/main/java/com/aether/agent/service/AdminPreferenceExtractionService.java`:

```java
package com.aether.agent.service;

import com.aether.agent.model.AgentDefinition;
import com.aether.agent.model.AgentMessage;
import com.aether.agent.model.ModelProvider;

public interface AdminPreferenceExtractionService {

    void extractAsync(String userId, String conversationId,
                      AgentMessage userMessage, AgentMessage assistantMessage,
                      AgentDefinition agent, ModelProvider provider);
}
```

- [ ] **Step 2: Commit interface changes**

```bash
git add api/src/main/java/com/aether/agent/service/AdminPreferenceExtractionService.java
git commit -m "feat(preference): update AdminPreferenceExtractionService interface"
```

---

## Task 14: Extraction Service Implementation

**Files:**

- Rewrite: `biz/src/main/java/com/aether/agent/service/impl/AdminPreferenceExtractionServiceImpl.java`

- [ ] **Step 1: Rewrite AdminPreferenceExtractionServiceImpl**

Replace content of `biz/src/main/java/com/aether/agent/service/impl/AdminPreferenceExtractionServiceImpl.java`:

```java
package com.aether.agent.service.impl;

import com.aether.agent.model.AgentDefinition;
import com.aether.agent.model.AgentMessage;
import com.aether.agent.model.ModelProvider;
import com.aether.agent.service.AdminPreferenceExtractionService;
import com.aether.sys.entity.AdminPreference;
import com.aether.sys.entity.AdminPreferenceEvent;
import com.aether.sys.mapper.AdminPreferenceMapper;
import com.aether.sys.service.AdminPreferenceEventService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AdminPreferenceExtractionServiceImpl implements AdminPreferenceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(AdminPreferenceExtractionServiceImpl.class);

    private static final BigDecimal MIN_CONFIDENCE = BigDecimal.valueOf(0.60);
    private static final BigDecimal DEFAULT_CONFIDENCE = BigDecimal.valueOf(0.80);
    private static final BigDecimal CONFIDENCE_REDUCE_ON_DUPLICATE = BigDecimal.valueOf(0.10);
    private static final int MAX_CONTENT_LENGTH = 512;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Autowired
    private AdminPreferenceMapper preferenceMapper;

    @Autowired
    private AdminPreferenceEventService eventService;

    @Override
    @Async
    public void extractAsync(String userId, String conversationId,
                             AgentMessage userMessage, AgentMessage assistantMessage,
                             AgentDefinition agent, ModelProvider provider) {
        try {
            doExtract(userId, conversationId, userMessage, assistantMessage);
        } catch (Exception e) {
            log.error("Failed to extract preferences for user {}", userId, e);
        }
    }

    private void doExtract(String userId, String conversationId,
                           AgentMessage userMessage, AgentMessage assistantMessage) {
        String extractionPrompt = buildExtractionPrompt(userMessage, assistantMessage);

        String response = callModel(extractionPrompt, provider);
        if (StringUtils.isBlank(response)) {
            return;
        }

        parseAndSavePreferences(userId, conversationId, response);
    }

    private String buildExtractionPrompt(AgentMessage userMessage, AgentMessage assistantMessage) {
        return "Extract stable, long-term preferences from this conversation.\n" +
                "Return JSON array: [{\"category\":\"language|style|format|tech_stack|tool_strategy\",\"key_name\":\"preference_key\",\"value\":\"preference_value\",\"confidence\":0.0-1.0}]\n" +
                "Exclude: one-time tasks, temporary questions, passwords, tokens.\n\n" +
                "User: " + userMessage.getContent() + "\n" +
                "Assistant: " + assistantMessage.getContent();
    }

    private String callModel(String prompt, ModelProvider provider) {
        try {
            return provider.chat(prompt);
        } catch (Exception e) {
            log.error("Failed to call model for preference extraction", e);
            return null;
        }
    }

    private void parseAndSavePreferences(String userId, String conversationId, String response) {
        String json = response;
        if (json.contains("```json")) {
            json = json.substring(json.indexOf("```json") + 7, json.lastIndexOf("```"));
        } else if (json.contains("```")) {
            json = json.substring(json.indexOf("```") + 3, json.lastIndexOf("```"));
        }

        try {
            org.json.JSONArray arr = new org.json.JSONArray(json.trim());
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                String category = obj.optString("category", "general");
                String keyName = obj.optString("key_name", "");
                String value = obj.optString("value", "");
                BigDecimal confidence = obj.optBigDecimal("confidence", DEFAULT_CONFIDENCE);

                if (StringUtils.isBlank(value) || value.length() > MAX_CONTENT_LENGTH) {
                    continue;
                }
                if (confidence.compareTo(MIN_CONFIDENCE) < 0) {
                    continue;
                }

                savePreference(userId, conversationId, category, keyName, value, confidence);
            }
        } catch (Exception e) {
            log.error("Failed to parse extraction response", e);
        }
    }

    private void savePreference(String userId, String conversationId,
                                String category, String keyName, String value, BigDecimal confidence) {
        AdminPreference existing = preferenceMapper.selectByKey(userId, keyName);
        if (existing != null) {
            if (existing.getValue().equals(value)) {
                existing.setUsageCount(existing.getUsageCount() + 1);
                existing.setLastUsedAt(System.currentTimeMillis());
                preferenceMapper.updateById(existing);
                return;
            }
            confidence = confidence.subtract(CONFIDENCE_REDUCE_ON_DUPLICATE);
        }

        AdminPreference pref = new AdminPreference();
        pref.setAdminId(userId);
        pref.setCategory(category);
        pref.setKeyName(keyName);
        pref.setValue(value);
        pref.setDescription(value);
        pref.setPriority(50);
        pref.setScope(AdminPreference.SCOPE_GLOBAL);
        pref.setSource(AdminPreference.SOURCE_IMPLICIT);
        pref.setConfidence(confidence);
        pref.setUsageCount(0);
        pref.setDecayRate(BigDecimal.ZERO);
        pref.setEffectiveScore(BigDecimal.valueOf(50));
        pref.setStatus(AdminPreference.STATUS_ENABLED);
        pref.setCreatedAt(System.currentTimeMillis());
        pref.setUpdatedAt(System.currentTimeMillis());
        preferenceMapper.insert(pref);

        AdminPreferenceEvent event = new AdminPreferenceEvent();
        event.setAdminId(userId);
        event.setPreferenceId(pref.getId());
        event.setEventType(AdminPreferenceEvent.EVENT_EXTRACT);
        event.setCategory(category);
        event.setKeyName(keyName);
        event.setValue(value);
        event.setConfidence(confidence);
        event.setConversationId(conversationId);
        event.setCreatedAt(System.currentTimeMillis());
        eventService.logEvent(event);
    }
}
```

- [ ] **Step 2: Commit extraction service implementation**

```bash
git add biz/src/main/java/com/aether/agent/service/impl/AdminPreferenceExtractionServiceImpl.java
git commit -m "feat(preference): rewrite extraction service for new schema"
```

---

## Task 15: Controller Layer - AdminPreferenceController

**Files:**

- Rewrite: `admin/src/main/java/com/aether/sys/controller/AdminPreferenceController.java`

- [ ] **Step 1: Rewrite AdminPreferenceController**

Replace content of `admin/src/main/java/com/aether/sys/controller/AdminPreferenceController.java`:

```java
package com.aether.sys.controller;

import com.aether.common.utils.CurrentUser;
import com.aether.common.utils.StringUtils;
import com.aether.permission.Permission;
import com.aether.sys.entity.AdminPreference;
import com.aether.sys.entity.AdminPreferenceEvent;
import com.aether.sys.service.AdminPreferenceEventService;
import com.aether.sys.service.AdminPreferenceService;
import com.aether.sys.vo.AdminPreferenceVo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sys/admin/preference")
public class AdminPreferenceController {

    @Autowired
    private AdminPreferenceService preferenceService;

    @Autowired
    private AdminPreferenceEventService eventService;

    @PostMapping("/list")
    @Permission(path = "/sys/admin/preference", type = Permission.Type.Read)
    public Object list(@RequestBody AdminPreferenceVo vo) {
        String adminId = StringUtils.isNotBlank(vo.getAdminId()) ? vo.getAdminId() : CurrentUser.getId();
        Page<AdminPreference> page = new Page<>(vo.getCurrent() != null ? vo.getCurrent() : 1, vo.getPageSize() != null ? vo.getPageSize() : 10);
        Page<AdminPreference> result = preferenceService.page(page,
                Wrappers.lambdaQuery(AdminPreference.class)
                        .eq(AdminPreference::getAdminId, adminId)
                        .eq(AdminPreference::getDeleted, false)
                        .like(StringUtils.isNotBlank(vo.getKeyName()), AdminPreference::getKeyName, vo.getKeyName())
                        .like(StringUtils.isNotBlank(vo.getValue()), AdminPreference::getValue, vo.getValue())
                        .eq(vo.getStatus() != null, AdminPreference::getStatus, vo.getStatus())
                        .orderByDesc(AdminPreference::getEffectiveScore));
        return result;
    }

    @GetMapping("/{id}")
    @Permission(path = "/sys/admin/preference", type = Permission.Type.Read)
    public Object detail(@PathVariable String id) {
        AdminPreference pref = preferenceService.getById(id);
        if (pref == null || pref.getDeleted()) {
            throw new RuntimeException("Preference not found");
        }
        return pref;
    }

    @PostMapping
    @Permission(path = "/sys/admin/preference", type = Permission.Type.Write)
    public Object save(@RequestBody AdminPreferenceVo vo) {
        AdminPreference pref = new AdminPreference();
        BeanUtils.copyProperties(vo, pref);
        if (StringUtils.isBlank(pref.getAdminId())) {
            pref.setAdminId(CurrentUser.getId());
        }
        if (pref.getStatus() == null) {
            pref.setStatus(AdminPreference.STATUS_ENABLED);
        }
        if (pref.getPriority() == null) {
            pref.setPriority(50);
        }
        if (StringUtils.isBlank(pref.getScope())) {
            pref.setScope(AdminPreference.SCOPE_GLOBAL);
        }
        if (StringUtils.isBlank(pref.getSource())) {
            pref.setSource(AdminPreference.SOURCE_EXPLICIT);
        }
        if (pref.getConfidence() == null) {
            pref.setConfidence(BigDecimal.ONE);
        }
        if (pref.getDecayRate() == null) {
            pref.setDecayRate(BigDecimal.ZERO);
        }
        if (pref.getEffectiveScore() == null) {
            pref.setEffectiveScore(BigDecimal.valueOf(50));
        }
        preferenceService.save(pref);
        preferenceService.clearUserCache(pref.getAdminId());
        return pref;
    }

    @PutMapping("/{id}")
    @Permission(path = "/sys/admin/preference", type = Permission.Type.Write)
    public Object update(@PathVariable String id, @RequestBody AdminPreferenceVo vo) {
        AdminPreference existing = preferenceService.getById(id);
        if (existing == null || existing.getDeleted()) {
            throw new RuntimeException("Preference not found");
        }
        BeanUtils.copyProperties(vo, existing, "id");
        existing.setUpdatedAt(System.currentTimeMillis());
        preferenceService.updateById(existing);
        preferenceService.clearUserCache(existing.getAdminId());
        return existing;
    }

    @DeleteMapping("/{id}")
    @Permission(path = "/sys/admin/preference", type = Permission.Type.Write)
    public Object delete(@PathVariable String id) {
        AdminPreference existing = preferenceService.getById(id);
        if (existing == null) {
            throw new RuntimeException("Preference not found");
        }
        preferenceService.removeById(id);
        preferenceService.clearUserCache(existing.getAdminId());
        return true;
    }

    @PutMapping("/{id}/status")
    @Permission(path = "/sys/admin/preference", type = Permission.Type.Write)
    public Object updateStatus(@PathVariable String id, @RequestBody AdminPreferenceVo vo) {
        AdminPreference existing = preferenceService.getById(id);
        if (existing == null || existing.getDeleted()) {
            throw new RuntimeException("Preference not found");
        }
        existing.setStatus(vo.getStatus());
        existing.setUpdatedAt(System.currentTimeMillis());
        preferenceService.updateById(existing);
        preferenceService.clearUserCache(existing.getAdminId());
        return existing;
    }

    @PostMapping("/{id}/feedback")
    @Permission(path = "/sys/admin/preference", type = Permission.Type.Write)
    public Object confirm(@PathVariable String id) {
        AdminPreference existing = preferenceService.getById(id);
        if (existing == null || existing.getDeleted()) {
            throw new RuntimeException("Preference not found");
        }
        preferenceService.adjustConfidence(id, BigDecimal.valueOf(0.10));
        preferenceService.incrementUsage(id);

        AdminPreferenceEvent event = new AdminPreferenceEvent();
        event.setAdminId(existing.getAdminId());
        event.setPreferenceId(id);
        event.setEventType(AdminPreferenceEvent.EVENT_CONFIRM);
        event.setCategory(existing.getCategory());
        event.setKeyName(existing.getKeyName());
        event.setValue(existing.getValue());
        event.setConfidence(existing.getConfidence());
        event.setCreatedAt(System.currentTimeMillis());
        eventService.logEvent(event);

        return true;
    }

    @DeleteMapping("/{id}/feedback")
    @Permission(path = "/sys/admin/preference", type = Permission.Type.Write)
    public Object reject(@PathVariable String id) {
        AdminPreference existing = preferenceService.getById(id);
        if (existing == null || existing.getDeleted()) {
            throw new RuntimeException("Preference not found");
        }
        preferenceService.adjustConfidence(id, BigDecimal.valueOf(-0.30));

        AdminPreferenceEvent event = new AdminPreferenceEvent();
        event.setAdminId(existing.getAdminId());
        event.setPreferenceId(id);
        event.setEventType(AdminPreferenceEvent.EVENT_REJECT);
        event.setCategory(existing.getCategory());
        event.setKeyName(existing.getKeyName());
        event.setValue(existing.getValue());
        event.setConfidence(existing.getConfidence());
        event.setCreatedAt(System.currentTimeMillis());
        eventService.logEvent(event);

        return true;
    }

    @PutMapping("/{id}/override")
    @Permission(path = "/sys/admin/preference", type = Permission.Type.Write)
    public Object override(@PathVariable String id, @RequestBody AdminPreferenceVo vo) {
        AdminPreference existing = preferenceService.getById(id);
        if (existing == null || existing.getDeleted()) {
            throw new RuntimeException("Preference not found");
        }

        AdminPreferenceEvent event = new AdminPreferenceEvent();
        event.setAdminId(existing.getAdminId());
        event.setPreferenceId(id);
        event.setEventType(AdminPreferenceEvent.EVENT_OVERRIDE);
        event.setCategory(existing.getCategory());
        event.setKeyName(existing.getKeyName());
        event.setValue(existing.getValue());
        event.setConfidence(existing.getConfidence());
        event.setCreatedAt(System.currentTimeMillis());
        eventService.logEvent(event);

        existing.setValue(vo.getValue());
        if (StringUtils.isNotBlank(vo.getDescription())) {
            existing.setDescription(vo.getDescription());
        }
        existing.setSource(AdminPreference.SOURCE_EXPLICIT);
        existing.setDecayRate(BigDecimal.ZERO);
        existing.setConfidence(BigDecimal.ONE);
        existing.setUpdatedAt(System.currentTimeMillis());
        preferenceService.updateById(existing);
        preferenceService.clearUserCache(existing.getAdminId());

        return existing;
    }
}
```

- [ ] **Step 2: Commit controller changes**

```bash
git add admin/src/main/java/com/aether/sys/controller/AdminPreferenceController.java
git commit -m "feat(preference): rewrite controller with feedback endpoints"
```

---

## Task 16: Context Injection - KnowledgeContextService

**Files:**

- Modify: `biz/src/main/java/com/aether/agent/service/KnowledgeContextService.java`

- [ ] **Step 1: Update KnowledgeContextService to use new reasoning engine**

Find the section where `buildPreferenceContext` is called and update it to pass taskType parameter.

The current code should be something like:

```java
String preferenceContext = preferenceService.buildPreferenceContext(userId);
```

Update to:

```java
String preferenceContext = preferenceService.buildPreferenceContext(userId, null);
```

- [ ] **Step 2: Commit context injection changes**

```bash
git add biz/src/main/java/com/aether/agent/service/KnowledgeContextService.java
git commit -m "feat(preference): update KnowledgeContextService to use new preference API"
```

---

## Task 17: Build and Verify

- [ ] **Step 1: Clean and compile**

```bash
mvn clean compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Run tests**

```bash
mvn test
```

Expected: All tests pass

- [ ] **Step 3: Commit any fixes**

```bash
git add -A
git commit -m "fix(preference): resolve compilation and test issues"
```

---

## Task 18: Data Migration Script

**Files:**

- Create: `api/src/main/resources/sql/migration/preference-migration.sql`

- [ ] **Step 1: Create migration script**

Create `api/src/main/resources/sql/migration/preference-migration.sql`:

```sql
-- Migration script: old sys_admin_preference to new schema
-- Run this after applying the new DDL

-- Step 1: Rename old table
ALTER TABLE sys_admin_preference RENAME TO sys_admin_preference_old;

-- Step 2: Create new table (use new DDL)

-- Step 3: Migrate data
INSERT INTO sys_admin_preference (
    id, admin_id, category, key_name, value, description,
    priority, scope, source, confidence, status,
    usage_count, last_used_at, expires_at, decay_rate, effective_score,
    created_at, updated_at, deleted
)
SELECT
    id, admin_id, category,
    CONCAT('preference_', id) as key_name,
    content as value,
    content as description,
    50 as priority,
    'global' as scope,
    'explicit' as source,
    confidence,
    status,
    0 as usage_count,
    updated_at as last_used_at,
    NULL as expires_at,
    0.00 as decay_rate,
    50.00 as effective_score,
    created_at, updated_at, deleted
FROM sys_admin_preference_old;

-- Step 4: Drop old table (uncomment after verification)
-- DROP TABLE sys_admin_preference_old;
```

- [ ] **Step 2: Commit migration script**

```bash
git add api/src/main/resources/sql/migration/preference-migration.sql
git commit -m "feat(preference): add data migration script"
```

---

## Completion Checklist

- [ ] All new tables created (sys_admin_preference, sys_admin_preference_event)
- [ ] Old table removed
- [ ] Dictionary dependency removed
- [ ] Entity layer rewritten with new fields
- [ ] VO layer rewritten
- [ ] Mapper layer updated with custom queries
- [ ] PreferenceReasoningEngine implemented with caching
- [ ] PreferenceDecayScheduler implemented
- [ ] AdminPreferenceEventService created
- [ ] Extraction service rewritten for new schema
- [ ] Controller rewritten with feedback endpoints
- [ ] KnowledgeContextService updated
- [ ] Migration script created
- [ ] All tests pass
- [ ] Build successful
