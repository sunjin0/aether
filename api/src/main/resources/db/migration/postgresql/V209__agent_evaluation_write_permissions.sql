INSERT INTO sys_resource (id,name,name_cn,path,type,icon,parent_id,leaf,description,state,deleted,created_at,updated_at,sort_num)
VALUES
 ('perm_eval_dataset_read','Read','可读',NULL,'Resource_Type_Permission',NULL,'agent_evaluation_datasets',TRUE,'查看评测集与版本',0,FALSE,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,1),
 ('perm_eval_dataset_write','Write','可写',NULL,'Resource_Type_Permission',NULL,'agent_evaluation_datasets',TRUE,'创建和维护评测集与版本',0,FALSE,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,2),
 ('perm_eval_evaluator_read','Read','可读',NULL,'Resource_Type_Permission',NULL,'agent_evaluation_evaluators',TRUE,'查看评分器与版本',0,FALSE,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,1),
 ('perm_eval_evaluator_write','Write','可写',NULL,'Resource_Type_Permission',NULL,'agent_evaluation_evaluators',TRUE,'创建和发布评分器版本',0,FALSE,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,2),
 ('perm_eval_experiment_read','Read','可读',NULL,'Resource_Type_Permission',NULL,'agent_evaluation_experiments',TRUE,'查看评测实验与报告',0,FALSE,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,1),
 ('perm_eval_experiment_write','Write','可写',NULL,'Resource_Type_Permission',NULL,'agent_evaluation_experiments',TRUE,'创建、取消和人工复核评测实验',0,FALSE,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,2)
ON CONFLICT (id) DO UPDATE SET name_cn=EXCLUDED.name_cn,parent_id=EXCLUDED.parent_id,deleted=FALSE,updated_at=EXCLUDED.updated_at;

WITH roles AS (
    SELECT id AS role_id FROM sys_role WHERE name = 'root' AND deleted = FALSE
    UNION
    SELECT DISTINCT role_id FROM sys_role_resource WHERE resource_id = 'agent_definition' AND deleted = FALSE
), resources AS (
    SELECT id FROM sys_resource WHERE id IN (
        'perm_eval_dataset_read','perm_eval_dataset_write',
        'perm_eval_evaluator_read','perm_eval_evaluator_write',
        'perm_eval_experiment_read','perm_eval_experiment_write'
    ) AND deleted = FALSE
)
INSERT INTO sys_role_resource (id,role_id,resource_id,state,deleted,created_at,updated_at,sort_num)
SELECT md5('agent-evaluation:'||roles.role_id||':'||resources.id),roles.role_id,resources.id,0,FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,1
FROM roles CROSS JOIN resources
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource x
    WHERE x.role_id=roles.role_id AND x.resource_id=resources.id AND x.deleted=FALSE
);
