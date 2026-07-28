-- V2__Backfill_missing_user_suspension_records.sql
-- Backfill safety migration for users created without suspension records

INSERT INTO user_suspension_record (
    user_id,
    report_count,
    suspension_end_date,
    is_permanently_banned,
    ban_reason,
    created_at,
    updated_at
)
SELECT
    u.user_id,
    0,
    NULL,
    FALSE,
    NULL,
    NOW(),
    NOW()
FROM users u
LEFT JOIN user_suspension_record usr ON usr.user_id = u.user_id
WHERE usr.user_id IS NULL;
