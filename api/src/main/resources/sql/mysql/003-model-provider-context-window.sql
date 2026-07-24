-- Add a configurable model context window to existing MySQL databases.
SET @context_window_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'agent_model_provider'
      AND COLUMN_NAME = 'context_window'
);
SET @context_window_ddl = IF(
    @context_window_exists = 0,
    'ALTER TABLE agent_model_provider ADD COLUMN context_window INT NOT NULL DEFAULT 32768 COMMENT ''模型上下文窗口大小（token）'' AFTER default_model',
    'SELECT 1'
);
PREPARE context_window_stmt FROM @context_window_ddl;
EXECUTE context_window_stmt;
DEALLOCATE PREPARE context_window_stmt;
