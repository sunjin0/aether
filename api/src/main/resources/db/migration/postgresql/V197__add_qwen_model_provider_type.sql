-- Qwen uses the DashScope OpenAI-compatible endpoint but has vendor-specific
-- multimodal request semantics. Make it an explicit selectable provider type.
INSERT INTO sys_dict (id, code, parent, name, name_cn, val, remark, state, deleted, created_at, updated_at, sort_num)
SELECT nextval('sys_dict_id_seq')::text,
       'Model_Provider_Type_Qwen',
       'Model_Provider_Type',
       'Qwen',
       '通义千问',
       'qwen-compatible',
       'Qwen / DashScope OpenAI-compatible model provider',
       1,
       FALSE,
       EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::bigint,
       EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::bigint,
       5
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict WHERE code = 'Model_Provider_Type_Qwen' AND deleted = FALSE
);

-- Migrate existing DashScope records away from the generic OpenAI adapter.
UPDATE agent_model_provider
SET type = 'qwen-compatible'
WHERE type = 'openai'
  AND (LOWER(COALESCE(name, '')) LIKE '%qwen%'
       OR LOWER(COALESCE(api_base_url, '')) LIKE '%dashscope.aliyuncs.com%');
