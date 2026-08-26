-- Existing exports predate durable delivery tracking. They remain audit-only and
-- must not be resent automatically because their SMTP outcome is unknowable.
ALTER TABLE gifticon_export_batches
    ADD COLUMN delivery_status VARCHAR(32) NULL AFTER item_count,
    ADD COLUMN delivery_attempt_count INT NOT NULL DEFAULT 0 AFTER delivery_status,
    ADD COLUMN last_attempt_at DATETIME(6) NULL AFTER delivery_attempt_count,
    ADD COLUMN next_attempt_at DATETIME(6) NULL AFTER last_attempt_at,
    ADD COLUMN submitted_at DATETIME(6) NULL AFTER next_attempt_at,
    ADD COLUMN last_error VARCHAR(1000) NULL AFTER submitted_at;

UPDATE gifticon_export_batches
SET delivery_status = 'LEGACY_UNKNOWN'
WHERE delivery_status IS NULL;

ALTER TABLE gifticon_export_batches
    MODIFY COLUMN delivery_status VARCHAR(32) NOT NULL,
    ADD CONSTRAINT chk_gifticon_export_delivery_status
        CHECK (delivery_status IN ('READY', 'SUBMITTED', 'FAILED', 'LEGACY_UNKNOWN'));

ALTER TABLE gifticon_export_batches
    ADD INDEX idx_gifticon_export_delivery_due
        (delivery_status, next_attempt_at, export_batch_id);

-- Supports the bounded, deterministic unbatched queue scan and batch rebuild scan.
ALTER TABLE gifticon_purchases
    DROP INDEX idx_gifticon_purchase_export,
    ADD INDEX idx_gifticon_purchase_export_queue
        (export_batch_id, requested_at, purchase_id);
