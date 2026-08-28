-- Keep the same winner that the application historically selected: the greatest token id.
-- Older unused rows can otherwise become valid again after the newest row is consumed.
ALTER TABLE email_verification_tokens
    ADD INDEX idx_evt_email_used_id (email, used_at, id);

DELETE stale
FROM email_verification_tokens stale
JOIN email_verification_tokens newer
  ON CONVERT(LOWER(TRIM(newer.email)) USING utf8mb4) COLLATE utf8mb4_bin
     = CONVERT(LOWER(TRIM(stale.email)) USING utf8mb4) COLLATE utf8mb4_bin
 AND newer.used_at IS NULL
 AND newer.id > stale.id
WHERE stale.used_at IS NULL;

-- MySQL permits multiple NULL values in a unique key. Only unused rows keep their canonical
-- trim/lower email here, so used-token history remains while case variants share one active code.
ALTER TABLE email_verification_tokens
    ADD COLUMN active_email varchar(255)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL AFTER email;

UPDATE email_verification_tokens
SET active_email = CASE
        WHEN used_at IS NULL THEN LOWER(TRIM(email))
        ELSE NULL
    END;

ALTER TABLE email_verification_tokens
    ADD CONSTRAINT chk_email_verification_active_email
        CHECK (
            (
                used_at IS NULL
                AND active_email IS NOT NULL
                AND active_email = CONVERT(LOWER(TRIM(email)) USING utf8mb4) COLLATE utf8mb4_bin
            )
            OR
            (used_at IS NOT NULL AND active_email IS NULL)
        ),
    ADD CONSTRAINT uk_email_verification_active_email UNIQUE (active_email);

-- Fixed database rows serialize issuance and verification even when an email has no token yet.
-- Java String.hashCode of the trimmed, lower-case email modulo 64 selects the row.
CREATE TABLE email_verification_lock_buckets (
    bucket_id smallint NOT NULL,
    PRIMARY KEY (bucket_id)
) engine=InnoDB;

INSERT INTO email_verification_lock_buckets (bucket_id) VALUES
    (0),  (1),  (2),  (3),  (4),  (5),  (6),  (7),
    (8),  (9),  (10), (11), (12), (13), (14), (15),
    (16), (17), (18), (19), (20), (21), (22), (23),
    (24), (25), (26), (27), (28), (29), (30), (31),
    (32), (33), (34), (35), (36), (37), (38), (39),
    (40), (41), (42), (43), (44), (45), (46), (47),
    (48), (49), (50), (51), (52), (53), (54), (55),
    (56), (57), (58), (59), (60), (61), (62), (63);
