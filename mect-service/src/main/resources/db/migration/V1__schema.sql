-- MECT schema: LMR, seasonal capacity, blocking flags, outbox, idempotency

CREATE TABLE lmr (
    id BIGSERIAL PRIMARY KEY,
    lmr_id VARCHAR(64) NOT NULL,
    planning_year VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(lmr_id, planning_year)
);
CREATE INDEX idx_lmr_lmr_id_planning_year ON lmr(lmr_id, planning_year);

CREATE TABLE lmr_seasonal_capacity (
    lmr_entity_id BIGINT NOT NULL REFERENCES lmr(id) ON DELETE CASCADE,
    season VARCHAR(32) NOT NULL,
    mw DOUBLE PRECISION NOT NULL
);

CREATE TABLE lmr_blocking_flags (
    lmr_entity_id BIGINT NOT NULL REFERENCES lmr(id) ON DELETE CASCADE,
    flag VARCHAR(64) NOT NULL
);

CREATE TABLE outbox_entry (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    message_key VARCHAR(256),
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);
CREATE INDEX idx_outbox_published ON outbox_entry(published_at);

CREATE TABLE processed_event (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    processed_at TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX idx_processed_event_id ON processed_event(event_id);
