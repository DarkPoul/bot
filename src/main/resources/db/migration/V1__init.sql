CREATE TABLE IF NOT EXISTS users (
    user_id INTEGER PRIMARY KEY,
    username TEXT,
    full_name TEXT,
    location_id TEXT,
    phone TEXT,
    role TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT,
    created_by INTEGER
);

CREATE TABLE IF NOT EXISTS locations (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    address TEXT,
    active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS location_assignments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    location_id TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    is_primary INTEGER NOT NULL,
    active_from TEXT,
    active_to TEXT,
    FOREIGN KEY (location_id) REFERENCES locations(id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS shifts (
    shift_id TEXT PRIMARY KEY,
    date TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    location_id TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    status TEXT NOT NULL,
    source TEXT NOT NULL,
    linked_request_id TEXT,
    updated_at TEXT,
    FOREIGN KEY (location_id) REFERENCES locations(id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS requests (
    request_id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    initiator_user_id INTEGER NOT NULL,
    from_user_id INTEGER,
    to_user_id INTEGER,
    date TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    location_id TEXT NOT NULL,
    status TEXT NOT NULL,
    comment TEXT,
    created_at TEXT,
    updated_at TEXT,
    FOREIGN KEY (location_id) REFERENCES locations(id)
);

CREATE TABLE IF NOT EXISTS access_requests (
    id TEXT PRIMARY KEY,
    telegram_user_id INTEGER NOT NULL,
    username TEXT,
    full_name TEXT,
    comment TEXT,
    status TEXT NOT NULL,
    created_at TEXT,
    processed_by INTEGER,
    processed_at TEXT
);

CREATE TABLE IF NOT EXISTS substitution_requests (
    id TEXT PRIMARY KEY,
    created_at TEXT,
    seller_telegram_id INTEGER NOT NULL,
    seller_name TEXT,
    location TEXT,
    shift_date TEXT,
    reason_code TEXT,
    reason_text TEXT,
    status TEXT NOT NULL,
    processed_by INTEGER,
    processed_at TEXT
);

CREATE TABLE IF NOT EXISTS schedules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    location_id TEXT,
    work_date TEXT NOT NULL,
    is_working INTEGER NOT NULL DEFAULT 1,
    created_at TEXT,
    UNIQUE (user_id, work_date),
    FOREIGN KEY (location_id) REFERENCES locations(id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    event_id TEXT PRIMARY KEY,
    timestamp TEXT NOT NULL,
    actor_user_id INTEGER NOT NULL,
    action TEXT NOT NULL,
    entity_type TEXT,
    entity_id TEXT,
    details TEXT
);

CREATE INDEX IF NOT EXISTS idx_users_tg_id ON users(user_id);
CREATE INDEX IF NOT EXISTS idx_schedules_user_date ON schedules(user_id, work_date);
CREATE INDEX IF NOT EXISTS idx_schedules_location_date ON schedules(location_id, work_date);
CREATE INDEX IF NOT EXISTS idx_requests_status_date ON requests(status, date, location_id);
CREATE INDEX IF NOT EXISTS idx_substitution_status_date ON substitution_requests(status, shift_date, location);
CREATE INDEX IF NOT EXISTS idx_access_requests_status ON access_requests(status);
