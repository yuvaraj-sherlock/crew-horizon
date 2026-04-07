-- ============================================================
-- Notification Service — Flyway Migration V1: Initial Schema
-- ============================================================
CREATE TABLE IF NOT EXISTS notifications (
    id                      BIGSERIAL PRIMARY KEY,
    recipient_employee_id   VARCHAR(20)  NOT NULL,
    recipient_email         VARCHAR(255) NOT NULL,
    notification_type       VARCHAR(30)  NOT NULL,
    channel                 VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
    subject                 VARCHAR(255) NOT NULL,
    body                    TEXT         NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count             INTEGER      NOT NULL DEFAULT 0,
    error_message           VARCHAR(500),
    sent_at                 TIMESTAMP,
    reference_id            VARCHAR(50),
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notif_recipient ON notifications(recipient_employee_id);
CREATE INDEX IF NOT EXISTS idx_notif_status    ON notifications(status);
CREATE INDEX IF NOT EXISTS idx_notif_type      ON notifications(notification_type);
CREATE INDEX IF NOT EXISTS idx_notif_created   ON notifications(created_at);
-- Partial index for retry query — only index FAILED notifications
CREATE INDEX IF NOT EXISTS idx_notif_retry     ON notifications(status, retry_count) WHERE status = 'FAILED';
