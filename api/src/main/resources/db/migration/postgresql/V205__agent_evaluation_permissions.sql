INSERT INTO sys_resource (id,name,name_cn,path,type,icon,parent_id,leaf,description,state,deleted,created_at,updated_at,sort_num)
VALUES ('agent_evaluation','Agent Evaluation','Agent 评测中心','/evaluation','Resource_Type_Route','experiment','menu_agent',FALSE,'Agent 与工作流统一评测中心',0,FALSE,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,20)
ON CONFLICT (id) DO UPDATE SET name_cn=EXCLUDED.name_cn,path=EXCLUDED.path,parent_id=EXCLUDED.parent_id,deleted=FALSE,updated_at=EXCLUDED.updated_at;

WITH roles AS (SELECT DISTINCT role_id FROM sys_role_resource WHERE resource_id='agent_definition' AND deleted=FALSE), resources AS (SELECT id FROM sys_resource WHERE id IN ('agent_evaluation','agent_evaluation_datasets','agent_evaluation_evaluators','agent_evaluation_experiments') AND deleted=FALSE)
INSERT INTO sys_role_resource (id,role_id,resource_id,state,deleted,created_at,updated_at,sort_num)
SELECT md5('agent-evaluation:'||roles.role_id||':'||resources.id),roles.role_id,resources.id,0,FALSE,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,1 FROM roles CROSS JOIN resources
WHERE NOT EXISTS (SELECT 1 FROM sys_role_resource x WHERE x.role_id=roles.role_id AND x.resource_id=resources.id AND x.deleted=FALSE);
INSERT INTO sys_resource (id,name,name_cn,path,type,parent_id,leaf,description,state,deleted,created_at,updated_at,sort_num)
VALUES
 ('agent_evaluation_datasets','Evaluation Datasets','评测集','/evaluation/datasets','Resource_Type_Route','agent_evaluation',TRUE,'维护评测集和不可变版本',0,FALSE,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,1),
 ('agent_evaluation_evaluators','Evaluation Evaluators','评分器','/evaluation/evaluators','Resource_Type_Route','agent_evaluation',TRUE,'维护规则和模型评分器',0,FALSE,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,2),
 ('agent_evaluation_experiments','Evaluation Experiments','评测实验','/evaluation/experiments','Resource_Type_Route','agent_evaluation',TRUE,'执行评测并查看报告',0,FALSE,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,3)
ON CONFLICT (id) DO UPDATE SET name_cn=EXCLUDED.name_cn,path=EXCLUDED.path,deleted=FALSE,updated_at=EXCLUDED.updated_at;

WITH roles AS (SELECT DISTINCT role_id FROM sys_role_resource WHERE resource_id='agent_definition' AND deleted=FALSE), resources AS (SELECT id FROM sys_resource WHERE id IN ('agent_evaluation_datasets','agent_evaluation_evaluators','agent_evaluation_experiments') AND deleted=FALSE)
INSERT INTO sys_role_resource (id,role_id,resource_id,state,deleted,created_at,updated_at,sort_num)
SELECT md5('agent-evaluation:'||roles.role_id||':'||resources.id),roles.role_id,resources.id,0,FALSE,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,1 FROM roles CROSS JOIN resources
WHERE NOT EXISTS (SELECT 1 FROM sys_role_resource x WHERE x.role_id=roles.role_id AND x.resource_id=resources.id AND x.deleted=FALSE);
