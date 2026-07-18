# Admin Preference System Redesign - Design Specification

## 1. Overview

### 1.1 Purpose
Redesign the `sys_admin_preference` system to support dynamic preference reasoning, implicit learning with decay, and structured preference management. The current implementation is too simplistic with only `category + content` fields and lacks learning/decay capabilities.

### 1.2 Design Goals
- **Structured Data Model**: Hierarchical preferences with priority, scope, and decay support
- **Dynamic Reasoning**: Context-aware preference injection based on task type and priority
- **Implicit Learning**: Automatically extract preferences from user interactions
- **Decay Mechanism**: Preferences naturally decay over time if unused
- **User Control**: Explicit feedback loop for preference confirmation/rejection

### 1.3 Constraints
- Keep existing tech stack (MySQL/PostgreSQL + Redis)
- No dictionary dependency for category field
- No backward compatibility with old schema required
- Java 8, Spring Boot 2.7.18, MyBatis-Plus

---

## 2. Data Model

### 2.1 Core Table: `sys_admin_preference`

```sql
CREATE TABLE sys_admin_preference (
    id              BIGINT       NOT NULL PRIMARY KEY,
    admin_id        BIGINT       NOT NULL COMMENT 'User ID',
    category        VARCHAR(32)  NOT NULL COMMENT 'language/style/format/tech_stack/tool_strategy',
    key_name        VARCHAR(128) NOT NULL COMMENT 'Preference key: output_length/code_language/term_style etc.',
    value           VARCHAR(512) NOT NULL COMMENT 'Preference value',
    description     VARCHAR(256)          COMMENT 'Human-readable description',
    priority        INT          NOT NULL DEFAULT 50 COMMENT 'Priority 0-100',
    scope           VARCHAR(32)  NOT NULL DEFAULT 'global' COMMENT 'global/session/task_type',
    scope_detail    VARCHAR(64)           COMMENT 'Specific task type when scope=task_type',
    source          VARCHAR(16)  NOT NULL DEFAULT 'explicit' COMMENT 'explicit(manual)/implicit(auto-learned)',
    confidence      DECIMAL(4,2) NOT NULL DEFAULT 1.00,
    usage_count     INT          NOT NULL DEFAULT 0,
    last_used_at    BIGINT                COMMENT 'Last used timestamp',
    expires_at      BIGINT                COMMENT 'Expiration time, NULL=never expires',
    decay_rate      DECIMAL(4,2) NOT NULL DEFAULT 0.00 COMMENT 'Daily decay rate, 0=no decay',
    effective_score DECIMAL(6,2) NOT NULL DEFAULT 100.00 COMMENT 'Current effective score',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0=disabled 1=enabled',
    created_at      BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    KEY idx_admin_id (admin_id),
    KEY idx_admin_category (admin_id, category),
    KEY idx_admin_key (admin_id, key_name),
    KEY idx_expires (expires_at),
    KEY idx_effective (admin_id, effective_score)
);
```

**Key Design Points:**
- `key_name` + `admin_id` unique semantic (one active record per user per preference key)
- `scope` + `scope_detail` supports task-type differentiation
- `effective_score` is dynamically calculated combining priority, usage, decay, and confidence
- `source` distinguishes manual vs auto-learned preferences

### 2.2 Event Log Table: `sys_admin_preference_event`

```sql
CREATE TABLE sys_admin_preference_event (
    id               BIGINT       NOT NULL PRIMARY KEY,
    admin_id         BIGINT       NOT NULL,
    preference_id    BIGINT                COMMENT 'Related preference ID, NULL=new discovery',
    event_type       VARCHAR(16)  NOT NULL COMMENT 'extract/confirm/reject/override/use',
    category         VARCHAR(32)           COMMENT 'Extracted category',
    key_name         VARCHAR(128)          COMMENT 'Extracted key',
    value            VARCHAR(512)          COMMENT 'Extracted value',
    confidence       DECIMAL(4,2),
    conversation_id  BIGINT                COMMENT 'Source conversation',
    message_id       BIGINT                COMMENT 'Source message',
    context_snapshot TEXT                  COMMENT 'Context summary at extraction time (JSON)',
    created_at       BIGINT       NOT NULL,
    KEY idx_admin_id (admin_id),
    KEY idx_admin_event (admin_id, event_type),
    KEY idx_created (created_at)
);
```

**Purpose:** Records all preference-related events for implicit learning analysis and audit trail.

---

## 3. Preference Reasoning Engine

### 3.1 Core Logic

The reasoning engine computes the "effective preference set" for each conversation based on context.

**Processing Flow:**
```
Input: userId + taskType + sessionContext
  │
  ▼
Step 1: Query all status=1 preferences for user
  │
  ▼
Step 2: Filter out expired (expires_at != NULL && expires_at < now)
  │
  ▼
Step 3: Group by key_name, sort within group by scope priority:
        task_type exact match > session > global
  │
  ▼
Step 4: Within same key_name, take highest effective_score
  │
  ▼
Step 5: Sort all effective preferences by effective_score descending
  │
  ▼
Output: Ordered effective preference list → System Prompt injection
```

### 3.2 Scope Matching Rules

| Scope | Match Condition | Priority |
|-------|----------------|----------|
| `task_type` | scope_detail == current task type | Highest (when matched) |
| `session` | Current session valid | Medium |
| `global` | Always applies | Lowest |

**Example:** User has `key_name=code_language` with both global=Java and task_type:frontend=TypeScript. Frontend tasks use TypeScript; backend tasks fall back to Java.

### 3.3 Effective Score Calculation

```
effective_score = priority × decay_factor × confidence
```

Where:
- `priority`: User-defined priority (0-100)
- `decay_factor`: Based on `decay_rate` and `last_used_at`
  - Formula: `decay_factor = max(0.1, 1.0 - decay_rate × days_since_last_use)`
  - No decay (decay_rate=0): decay_factor always 1.0
- `confidence`: Confidence score (0.00-1.00)

### 3.4 Output Format

Preferences are formatted for System Prompt injection:

```
【User Preferences (sorted by priority)】
- [language] Use TypeScript for frontend code (scope: task:frontend, priority: 80)
- [style] Answer in Chinese, keep technical terms in English (scope: global, priority: 90)
- [format] Add comments after code examples (scope: global, priority: 70)
```

### 3.5 Caching Strategy

- Redis cache, key = `pref:ctx:{adminId}:{taskType}`
- TTL = 5 minutes
- On preference change (add/edit/delete), proactively clear all user cache

---

## 4. Implicit Learning & Decay Mechanism

### 4.1 Implicit Learning Flow

Triggered asynchronously after each Agent conversation.

**Behavior Signals:**

| Signal | Meaning | Handling |
|--------|---------|----------|
| User requests "use Chinese/English" | Explicit language preference | High confidence extraction |
| User requests "be brief/detailed" | Output length preference | Medium confidence extraction |
| Language of code snippets user copies | Tech stack preference | Low confidence, requires accumulation |
| User requests "rewrite in TypeScript" | Language switch preference | High confidence extraction |
| User skips/ignores certain tool calls | Tool strategy preference | Low confidence, requires accumulation |
| User regenerates response | Dissatisfaction with answer | No direct preference adjustment, log event only |

**Learning Strategy:**
```
Step 1: LLM analyzes conversation, extracts candidates (category, key_name, value, confidence)
  │
  ▼
Step 2: For each candidate, check if same key_name preference exists
  │
  ├── Exists → Compare value
  │   ├── Same → usage_count +1, last_used_at = now, no new record
  │   └── Different → Add new record, confidence = initial_confidence - 0.10 (may be context-specific)
  │
  └── Not exists → Add new record, source=implicit
  │
  ▼
Step 3: Write to sys_admin_preference_event log
```

**Confidence Management:**
- AI-extracted initial confidence from model (range 0.6-1.0)
- Each user "use" → confidence += 0.05 (cap at 1.0)
- User explicit reject/override → confidence -= 0.20
- Confidence drops below 0.3 → auto-set status=0 (disabled)

### 4.2 Decay Mechanism

Decay causes preferences to "naturally age" over time, reducing weight of unused preferences.

**Decay Calculation (real-time during reasoning):**
```java
long daysSinceLastUse = (now - lastUsedAt) / (24 * 3600 * 1000);
double decayFactor = Math.max(0.1, 1.0 - decayRate * daysSinceLastUse);
double effectiveScore = priority * decayFactor * confidence;
```

**Default Decay Rates by Category:**

| Category | Default decay_rate | Notes |
|----------|-------------------|-------|
| language | 0.00 | Language preferences are stable, no decay |
| style | 0.005 | Style occasionally changes, very slow decay (200 days to 0) |
| format | 0.01 | Format preferences moderate decay (100 days to 0) |
| tech_stack | 0.02 | Tech stack changes faster (50 days to 0) |
| tool_strategy | 0.01 | Tool strategy moderate decay |

**Automatic Cleanup:**
- Daily scheduled task (using Spring `@Scheduled(cron = "0 0 2 * * ?")`) scans preferences with `effective_score < 10`
- These preferences are not deleted, but excluded from reasoning
- If user uses them again, score recovers

**Explicit Preferences Never Decay:**
- `source=explicit` preferences have `decay_rate=0`
- Only `source=implicit` (auto-learned) preferences decay

### 4.3 User Feedback Loop

Simple feedback mechanism to provide clear signals for implicit learning.

**New API Endpoints:**
- `POST /api/sys/admin/preference/{id}/feedback` — User confirms preference valid
- `DELETE /api/sys/admin/preference/{id}/feedback` — User rejects preference
- `PUT /api/sys/admin/preference/{id}/override` — User overrides preference value

**Behavior Impact:**
- confirm → confidence += 0.10, usage_count +1
- reject → confidence -= 0.30, if < 0.3 then auto-disable
- override → old value logged to event, new value takes effect, source changed to explicit

---

## 5. Agent Behavior Execution

### 5.1 Execution Chain

```
Preference Reasoning Engine Output (effective preference list)
  │
  ▼
Preference Executor (PreferenceExecutor)
  │
  ├── 1. Construct System Prompt fragment
  ├── 2. Set model parameter overrides
  └── 3. Mark tool call constraints
  │
  ▼
Inject into KnowledgeContextService.enhance()
```

### 5.2 System Prompt Dynamic Construction

Preferences are formatted into System Prompt instructions:

```
## Preference Instructions (auto-injected, sorted by priority)
- Answer in Chinese, keep technical terms in English
- Code examples use TypeScript by default
- Output style: concise and professional, avoid redundant explanations
- Add Chinese comments after code blocks
```

**Construction Rules:**
- Sorted by `effective_score` descending
- Same `key_name` only takes highest score
- Total length limited to 2000 characters, truncate by priority if exceeded

### 5.3 Model Parameter Overrides

Some preferences map to model call parameters:

| Preference key_name | Mapped Model Parameter |
|-------------------|----------------------|
| `response_length` | `max_tokens` adjustment (short→1024, medium→2048, long→4096) |
| `temperature` | `temperature` override |
| `language` | Specify output language in system prompt |

### 5.4 Tool Call Constraints (Reserved, Phase 2)

Current phase only constrains via System Prompt, no code-level interception:

```
## Tool Usage Preferences
- Ask for confirmation before executing Shell commands
- Prefer Maven over Gradle
```

---

## 6. Migration Strategy

### 6.1 Old to New Mapping

| Old Field | New Field | Notes |
|-----------|-----------|-------|
| `category` | `category` | Direct mapping, values may need normalization |
| `content` | `value` + `description` | Split content into value and human-readable description |
| `confidence` | `confidence` | Direct mapping |
| `status` | `status` | Direct mapping |
| `source_conversation_id` | N/A | Moved to event log |
| `source_message_id` | N/A | Moved to event log |

### 6.2 Migration Script

```sql
-- Step 1: Create new tables
-- (Use new DDL scripts)

-- Step 2: Migrate data
INSERT INTO sys_admin_preference (
    id, admin_id, category, key_name, value, description,
    priority, scope, source, confidence, status,
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
    created_at, updated_at, deleted
FROM sys_admin_preference_old;

-- Step 3: Drop old table
DROP TABLE sys_admin_preference_old;
```

---

## 7. Implementation Plan

### Phase 1: Core Refactoring (Current Implementation)

| Task | Files | Description |
|------|-------|-------------|
| New Table DDL | `api/src/main/resources/sql/mysql/001-schema.sql` etc. | Create `sys_admin_preference` + `sys_admin_preference_event` |
| Entity Refactoring | `api/.../entity/AdminPreference.java` | New field mappings, remove old fields |
| VO Refactoring | `api/.../vo/AdminPreferenceVo.java` | Match new entity |
| Mapper Refactoring | `api/.../mapper/AdminPreferenceMapper.java` | May need custom query methods |
| Service Refactoring | `biz/.../AdminPreferenceServiceImpl.java` | Add reasoning engine, decay calculation, caching |
| Extraction Refactoring | `biz/.../AdminPreferenceExtractionServiceImpl.java` | Rewrite learning logic for new schema |
| Controller Refactoring | `admin/.../AdminPreferenceController.java` | Add feedback endpoints, adjust list queries |
| Context Injection | `biz/.../KnowledgeContextService.java` | Switch to new reasoning engine |
| Remove Dictionary Dependency | Seed data | Remove `Admin_Preference_Category` from dict |

**Acceptance Criteria:**
- New preference system fully functional
- Old data migration script available
- Preference reasoning engine correctly sorts by scope and priority
- Implicit learning extracts new preferences and writes to event log
- Basic decay calculation working

### Phase 2: Tool Strategy + Session Override (Future)

- Tool call permission control (Shell command confirmation etc.)
- Natural language temporary override (`/verbose 3` shortcuts)
- Multi-scenario preference templates (coding/writing/research one-click switch)

### Phase 3: Advanced Learning (Future)

- Complex behavior signal analysis (copy content analysis, retry pattern recognition)
- Preference conflict auto-detection and resolution
- Preference migration (auto-adjust when user role changes)

---

## 8. Success Metrics

- **User Experience**: Reduce repeated instruction input by 20-30%
- **Automation**: 70%+ of active preferences auto-learned within 2 weeks
- **Personalization**: Preference-aware responses measurably preferred over generic ones
- **Resource Efficiency**: Effective preference injection adds < 50ms latency

---

## 9. Open Questions

- [ ] Should we support preference export/import between users/roles?
- [ ] How to handle preference conflicts when same key has multiple high-score entries?
- [ ] Should we add preference versioning for rollback capability?
