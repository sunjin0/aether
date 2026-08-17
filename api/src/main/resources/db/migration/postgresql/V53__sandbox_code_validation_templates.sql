-- Phase 4 fixed-command code validation templates.  Commands are immutable
-- template data, never supplied by Agents or requesters.  Images must contain
-- the declared tools/dependencies; network remains disabled.
INSERT INTO sandbox_execution_template
(id, code, name, description, enabled, risk_level, current_version_id, created_at, updated_at, sort_num, deleted, state)
VALUES ('sandbox_python_check', 'python-lint-test', 'Python 检查与测试',
        '固定 Python lint/test 命令，输入为受控 ZIP 快照', FALSE, 'HIGH', 'sandbox_python_check_v1',
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 30, FALSE, 0),
       ('sandbox_node_check', 'node-lint-test-build', 'Node 检查、测试与构建',
        '固定 Node lint/test/build 命令，输入为受控 ZIP 快照', FALSE, 'HIGH', 'sandbox_node_check_v1',
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 31, FALSE, 0)
ON CONFLICT
    (id)
    DO NOTHING;
INSERT INTO sandbox_execution_template_version
(id, template_id, version, published, config_snapshot, policy_version, published_at, created_at, updated_at, sort_num,
 deleted, state)
VALUES ('sandbox_python_check_v1', 'sandbox_python_check', 1, TRUE,
        '{"runtime":"PYTHON","executionMode":"FIXED_COMMAND","fixedCommand":"set -eu; archive=$(find /work/input -maxdepth 1 -type f -name \\\"*.zip\\\" -print -quit); test -n \"$archive\"; mkdir -p /work/output/workspace; unzip -q \"$archive\" -d /work/output/workspace; cd /work/output/workspace; python -m pytest --junitxml=/work/output/test-report.xml; python -m compileall -q .; printf \"{\\\"status\\\":\\\"passed\\\"}\\n\" > /work/output/summary.json","scriptSlot":false,"network":"NONE","dependencyPolicy":"PREINSTALLED_ONLY","inputFormats":["zip"],"maxInputFiles":1,"outputFormats":["xml","json","txt"],"timeoutSeconds":300,"maxOutputFiles":3,"maxOutputBytes":52428800,"maxInputBytes":10485760,"readOnlyRoot":true,"nonPrivileged":true}',
        'v1', (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 0, FALSE, 0),
       ('sandbox_node_check_v1', 'sandbox_node_check', 1, TRUE,
        '{"runtime":"NODE","executionMode":"FIXED_COMMAND","fixedCommand":"set -eu; archive=$(find /work/input -maxdepth 1 -type f -name \\\"*.zip\\\" -print -quit); test -n \"$archive\"; mkdir -p /work/output/workspace; unzip -q \"$archive\" -d /work/output/workspace; cd /work/output/workspace; npm run lint; npm test; npm run build; printf \"{\\\"status\\\":\\\"passed\\\"}\\n\" > /work/output/summary.json","scriptSlot":false,"network":"NONE","dependencyPolicy":"PREINSTALLED_ONLY","inputFormats":["zip"],"maxInputFiles":1,"outputFormats":["json","txt","zip"],"timeoutSeconds":300,"maxOutputFiles":3,"maxOutputBytes":52428800,"maxInputBytes":10485760,"readOnlyRoot":true,"nonPrivileged":true}',
        'v1', (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 0, FALSE, 0)
ON CONFLICT
DO NOTHING;
