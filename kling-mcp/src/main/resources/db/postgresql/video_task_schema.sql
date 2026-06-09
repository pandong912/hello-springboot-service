CREATE TABLE IF NOT EXISTS generation_tasks (
    task_id TEXT PRIMARY KEY,
    task_type TEXT NOT NULL,
    provider_task_id TEXT,
    owner_subject TEXT NOT NULL,
    client_id TEXT,
    organization_id TEXT,
    request_json TEXT NOT NULL,
    status TEXT NOT NULL,
    progress INTEGER,
    result_json TEXT,
    error_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_generation_tasks_type_provider_task_id
    ON generation_tasks (task_type, provider_task_id)
    WHERE provider_task_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_generation_tasks_type_owner_status_updated
    ON generation_tasks (task_type, owner_subject, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_generation_tasks_type_owner_updated
    ON generation_tasks (task_type, owner_subject, updated_at DESC);

CREATE TABLE IF NOT EXISTS task_callback_events (
    event_key TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
