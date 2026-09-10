WITH roles AS (
    SELECT id AS role_id FROM sys_role WHERE name = 'root' AND deleted = FALSE
    UNION
    SELECT DISTINCT role_id FROM sys_role_resource WHERE resource_id = 'agent_definition' AND deleted = FALSE
), resources AS (
    SELECT id FROM sys_resource
    WHERE id IN ('agent_evaluation','agent_evaluation_datasets','agent_evaluation_evaluators','agent_evaluation_experiments')
      AND deleted = FALSE
)
INSERT INTO sys_role_resource (id,role_id,resource_id,state,deleted,created_at,updated_at,sort_num)
SELECT md5('agent-evaluation:'||roles.role_id||':'||resources.id),roles.role_id,resources.id,0,FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,1
FROM roles CROSS JOIN resources
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource x
    WHERE x.role_id=roles.role_id AND x.resource_id=resources.id AND x.deleted=FALSE
);
