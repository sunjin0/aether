CREATE TABLE IF NOT EXISTS evaluation_worker_slot (
    id VARCHAR(32) PRIMARY KEY,
    phase VARCHAR(16) NOT NULL,
    slot_no INTEGER NOT NULL,
    task_id VARCHAR(32),
    lease_token VARCHAR(64),
    lease_until BIGINT,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT evaluation_worker_slot_phase_no_uk UNIQUE (phase, slot_no),
    CONSTRAINT evaluation_worker_slot_task_uk UNIQUE (task_id)
);

INSERT INTO evaluation_worker_slot (id, phase, slot_no, created_at, updated_at)
SELECT 'eval_exec_slot_' || value, 'EXECUTE', value, EXTRACT(EPOCH FROM clock_timestamp())::BIGINT * 1000, EXTRACT(EPOCH FROM clock_timestamp())::BIGINT * 1000
FROM generate_series(1, 8) AS value
ON CONFLICT (phase, slot_no) DO NOTHING;

INSERT INTO evaluation_worker_slot (id, phase, slot_no, created_at, updated_at)
SELECT 'eval_grade_slot_' || value, 'GRADE', value, EXTRACT(EPOCH FROM clock_timestamp())::BIGINT * 1000, EXTRACT(EPOCH FROM clock_timestamp())::BIGINT * 1000
FROM generate_series(1, 4) AS value
ON CONFLICT (phase, slot_no) DO NOTHING;
