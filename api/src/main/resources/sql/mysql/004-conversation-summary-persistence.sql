-- Persist rolling conversation summaries so Redis remains a cache rather than the fact source.
SET @summary_columns_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_conversation ADD COLUMN summary TEXT NULL COMMENT ''持久化会话摘要'' AFTER status, ADD COLUMN summary_covered_message_id BIGINT NULL COMMENT ''摘要覆盖到的消息ID'' AFTER summary, ADD COLUMN summary_covered_created_at BIGINT NULL COMMENT ''摘要覆盖到的消息创建时间'' AFTER summary_covered_message_id, ADD COLUMN summary_updated_at BIGINT NULL COMMENT ''摘要更新时间'' AFTER summary_covered_created_at',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'agent_conversation'
      AND COLUMN_NAME = 'summary'
);
PREPARE summary_columns_stmt FROM @summary_columns_ddl;
EXECUTE summary_columns_stmt;
DEALLOCATE PREPARE summary_columns_stmt;
