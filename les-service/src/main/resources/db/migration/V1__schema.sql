-- LES schema: enrollments, eligibility read-model, outbox, idempotency

CREATE TABLE lmr_enrollment (
    id BIGSERIAL PRIMARY KEY,
    lmr_id VARCHAR(64) NOT NULL UNIQUE,
    market_participant_name VARCHAR(256) NOT NULL,
    lmr_name VARCHAR(256) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    planning_year VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    withdraw_reject_reason VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_lmr_enrollment_lmr_id ON lmr_enrollment(lmr_id);

CREATE TABLE lmr_withdraw_eligibility (
    id BIGSERIAL PRIMARY KEY,
    planning_year VARCHAR(16) NOT NULL,
    lmr_id VARCHAR(64) NOT NULL,
    can_withdraw BOOLEAN NOT NULL,
    reason VARCHAR(512),
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(planning_year, lmr_id)
);
CREATE INDEX idx_eligibility_planning_lmr ON lmr_withdraw_eligibility(planning_year, lmr_id);

CREATE TABLE lmr_eligibility_blocking_flags (
    eligibility_id BIGINT NOT NULL REFERENCES lmr_withdraw_eligibility(id) ON DELETE CASCADE,
    flag VARCHAR(128) NOT NULL
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
