WITH duplicate_active AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY solution_id, application_id
        ORDER BY COALESCE(updated_at, created_at, 0) DESC, id DESC
    ) AS row_no
    FROM aether_solution_installation
    WHERE status = 1 AND deleted = FALSE
)
UPDATE aether_solution_installation installation
SET status = 0, updated_at = EXTRACT(EPOCH FROM NOW()) * 1000
FROM duplicate_active duplicate
WHERE installation.id = duplicate.id AND duplicate.row_no > 1;

CREATE UNIQUE INDEX IF NOT EXISTS aether_solution_active_install_uk
    ON aether_solution_installation(solution_id, application_id)
    WHERE status = 1 AND deleted = FALSE;
