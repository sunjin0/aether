INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at,
                          updated_at, sort_num)
VALUES ('knowledge_evaluation', 'Retrieval Evaluation', '检索评测', '/knowledge/evaluation', 'Resource_Type_Route',
        NULL, 'menu_knowledge', TRUE,
        'Manage retrieval evaluation datasets, runs, and quality trends / 管理检索评测集、运行记录与质量趋势', 0, FALSE,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 5)
ON CONFLICT
    (id)
    DO UPDATE SET path=EXCLUDED.path,name=EXCLUDED.name,name_cn=EXCLUDED.name_cn,deleted=FALSE,updated_at=EXCLUDED.updated_at;
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at,
                          updated_at, sort_num)
VALUES ('perm_knowledge_evaluation_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL,
        'knowledge_evaluation', TRUE, 'View retrieval evaluation datasets and reports / 查看检索评测集与报告', 0, FALSE,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 1),
       ('perm_knowledge_evaluation_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL,
        'knowledge_evaluation', TRUE,
        'Manage retrieval evaluation datasets and run evaluations / 管理检索评测集并执行评测', 0, FALSE,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 2)
ON CONFLICT
    (id)
    DO UPDATE SET deleted=FALSE,updated_at=EXCLUDED.updated_at;
INSERT INTO sys_role_resource (id, role_id, resource_id, created_at, updated_at, sort_num, deleted, state)
SELECT md5(r.id || ':' || resource_id),
       r.id,
       resource_id,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,0,
       FALSE,
       0
FROM sys_role r
         CROSS JOIN (VALUES ('knowledge_evaluation'),
                            ('perm_knowledge_evaluation_read'),
                            ('perm_knowledge_evaluation_write')) AS resources(resource_id)
WHERE r.name = 'root'
  AND r.deleted = FALSE
ON CONFLICT
DO NOTHING;
