-- AgentRun.status: 0-成功，1-失败，2-超时。
INSERT INTO sys_dict (id, code, parent, name, name_cn, val, remark, state, deleted, created_at, updated_at, sort_num) VALUES
(nextval('sys_dict_id_seq')::text, 'Agent_Run_Status_Success', 'Agent_Run_Status', 'Success', '成功', '0', '运行成功', 1, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
(nextval('sys_dict_id_seq')::text, 'Agent_Run_Status_Failed', 'Agent_Run_Status', 'Failed', '失败', '1', '运行失败', 1, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2),
(nextval('sys_dict_id_seq')::text, 'Agent_Run_Status_Timeout', 'Agent_Run_Status', 'Timeout', '超时', '2', '运行超时', 1, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 3);
