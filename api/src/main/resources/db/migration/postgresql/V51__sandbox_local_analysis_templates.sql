-- Phase 2 templates are intentionally disabled until the corresponding Runner
-- release and operational approval are in place. Their fixed snapshots never
-- accept caller-selected images, network, mounts, or commands.
INSERT INTO sandbox_execution_template (id, code, name, description, enabled, risk_level, current_version_id,
                                        created_at, updated_at, sort_num, deleted, state)
VALUES ('sandbox_local_python', 'local-python-analysis', '本地 Python 分析', '受控 Python 数据清洗、汇总和报告生成',
        FALSE, 'MEDIUM', 'sandbox_local_python_v1', (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 20, FALSE, 0),
       ('sandbox_local_node', 'local-node-analysis', '本地 Node 分析', '受控 Node 数据处理和报告生成', FALSE, 'MEDIUM',
        'sandbox_local_node_v1', (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 21, FALSE, 0)
ON CONFLICT
    (id)
    DO NOTHING;
INSERT INTO sandbox_execution_template_version (id, template_id, version, published, config_snapshot, policy_version,
                                                published_at, created_at, updated_at, sort_num, deleted, state)
VALUES ('sandbox_local_python_v1', 'sandbox_local_python', 1, TRUE,
        '{"runtime":"PYTHON","scriptSlot":true,"network":"NONE","outputFormats":["csv","json","md"],"timeoutSeconds":120,"maxOutputFiles":10,"maxOutputBytes":52428800,"maxInputBytes":1048576,"readOnlyRoot":true,"nonPrivileged":true}',
        'v1', (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 0, FALSE, 0),
       ('sandbox_local_node_v1', 'sandbox_local_node', 1, TRUE,
        '{"runtime":"NODE","scriptSlot":true,"network":"NONE","outputFormats":["csv","json","md"],"timeoutSeconds":120,"maxOutputFiles":10,"maxOutputBytes":52428800,"maxInputBytes":1048576,"readOnlyRoot":true,"nonPrivileged":true}',
        'v1', (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 0, FALSE, 0)
ON CONFLICT
    (id)
    DO NOTHING;
