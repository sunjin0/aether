-- 将已存在的运行记录投影到持续 Session/Task 模型。
-- 使用确定性 MD5 主键，保证迁移在备份恢复或重复执行时不会生成第二份历史数据。

INSERT INTO agent_session (id, conversation_id, agent_definition_id, user_id, status, graph_thread_id,
                           last_active_at, created_at, updated_at, sort_num, deleted, state)
SELECT DISTINCT
ON (run.conversation_id)
    md5('legacy-session:' || run.conversation_id),
    run.conversation_id,
    run.agent_definition_id,
    run.user_id,
    CASE WHEN conversation.status = 0 THEN 'ACTIVE' ELSE 'ARCHIVED' END,
    md5('legacy-session:' || run.conversation_id),
    run.created_at,
    run.created_at,
    run.created_at,
    0, FALSE, 0
FROM agent_run run
    JOIN agent_conversation conversation
ON conversation.id = run.conversation_id AND conversation.deleted = FALSE
WHERE run.session_id IS NULL
  AND run.conversation_id IS NOT NULL
  AND run.user_id IS NOT NULL
  AND run.agent_definition_id IS NOT NULL
  AND run.deleted = FALSE
ORDER BY run.conversation_id, run.created_at
DESC NULLS LAST, run.id DESC
ON CONFLICT (conversation_id)
DO NOTHING;

-- 若运行所属会话已由新链路创建，优先使用其真实 Session ID，而不是假设确定性 ID。
UPDATE agent_run run
SET session_id = session.id FROM agent_session session
WHERE run.session_id IS NULL
  AND run.conversation_id = session.conversation_id
  AND run.user_id = session.user_id
  AND run.deleted = FALSE
  AND session.deleted = FALSE;

INSERT INTO agent_task (id, session_id, user_id, agent_definition_id, title, status, current_run_id,
                        created_at, updated_at, sort_num, deleted, state)
SELECT md5('legacy-task:' || run.id),
       run.session_id,
       run.user_id,
       run.agent_definition_id,
       '历史运行 ' || left(run.id, 8),
       CASE run.status
           WHEN 0 THEN 'COMPLETED'
           WHEN 3 THEN 'QUEUED'
           WHEN 4 THEN 'PAUSED'
           WHEN 5 THEN 'CANCELLED'
           WHEN 6 THEN 'PAUSED'
           WHEN 1 THEN 'FAILED'
           WHEN 2 THEN 'FAILED'
           ELSE 'PAUSED'
           END,
       run.id,
       run.created_at,
       run.created_at,
       0,
       FALSE,
       0
FROM agent_run run
WHERE run.task_id IS NULL
  AND run.session_id IS NOT NULL
  AND run.deleted = FALSE
ON CONFLICT
    (id)
    DO NOTHING;

UPDATE agent_run run
SET task_id    = md5('legacy-task:' || run.id),
    attempt_no = COALESCE(run.attempt_no, 1)
WHERE run.task_id IS NULL
  AND run.session_id IS NOT NULL
  AND run.deleted = FALSE;

INSERT INTO agent_task_event (id, task_id, run_id, event_type, summary, occurred_at,
                              created_at, updated_at, sort_num, deleted, state)
SELECT md5('legacy-task-event:' || run.id),
       run.task_id,
       run.id,
       'task.migrated',
       '由历史 Agent 运行记录迁移生成',
       COALESCE(run.updated_at, run.created_at, 0),
       run.created_at,
       run.updated_at,
       0,
       FALSE,
       0
FROM agent_run run
WHERE run.task_id IS NOT NULL
  AND run.session_id IS NOT NULL
  AND run.deleted = FALSE
ON CONFLICT
    (id)
    DO NOTHING;

CREATE INDEX IF NOT EXISTS agent_task_session_status_idx
    ON agent_task(session_id, status, updated_at DESC);
