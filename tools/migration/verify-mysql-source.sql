-- Capture these values during the write freeze and compare them with verify-postgresql.sql.
SELECT 'sys_user' AS table_name, COUNT(*) AS row_count, MIN(id) AS min_id, MAX(id) AS max_id,
       SUM(CASE WHEN deleted = b'1' THEN 1 ELSE 0 END) AS deleted_count
FROM sys_user
UNION ALL
SELECT 'sys_dict', COUNT(*), MIN(id), MAX(id), SUM(CASE WHEN deleted = b'1' THEN 1 ELSE 0 END)
FROM sys_dict
UNION ALL
SELECT 'agent_document', COUNT(*), MIN(id), MAX(id), SUM(CASE WHEN deleted = b'1' THEN 1 ELSE 0 END)
FROM agent_document;

SELECT COUNT(*) AS user_role_orphans
FROM sys_user_role ur
LEFT JOIN sys_user u ON u.id = ur.user_id
LEFT JOIN sys_role r ON r.id = ur.role_id
WHERE u.id IS NULL OR r.id IS NULL;

SELECT COUNT(*) AS agent_document_orphans
FROM agent_document d
LEFT JOIN agent_knowledge_base kb ON kb.id = d.knowledge_base_id
WHERE kb.id IS NULL;
