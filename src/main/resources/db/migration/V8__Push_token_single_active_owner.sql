-- FCM registration is an ownership transfer: one token must have at most one active device row.
-- FCM tokens are opaque and case-sensitive, so both lookup and uniqueness use a binary collation.
ALTER TABLE push_devices
    MODIFY COLUMN fcm_token varchar(512)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    ADD COLUMN active_fcm_token varchar(512)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL AFTER fcm_token;

-- Preserve the most recently seen active owner. A primary-key tie-breaker makes the repair deterministic.
CREATE TEMPORARY TABLE tmp_push_token_active_winners (
    push_device_id bigint NOT NULL,
    PRIMARY KEY (push_device_id)
);

INSERT INTO tmp_push_token_active_winners (push_device_id)
SELECT current_device.push_device_id
FROM push_devices current_device
LEFT JOIN push_devices newer_device
       ON newer_device.fcm_token = current_device.fcm_token
      AND newer_device.enabled = b'1'
      AND (
            newer_device.last_seen_at > current_device.last_seen_at
         OR (
                newer_device.last_seen_at = current_device.last_seen_at
            AND newer_device.push_device_id > current_device.push_device_id
         )
      )
WHERE current_device.enabled = b'1'
  AND newer_device.push_device_id IS NULL;

UPDATE push_devices device
LEFT JOIN tmp_push_token_active_winners winner
       ON winner.push_device_id = device.push_device_id
SET device.enabled = CASE
        WHEN winner.push_device_id IS NULL THEN b'0'
        ELSE b'1'
    END,
    device.active_fcm_token = CASE
        WHEN winner.push_device_id IS NULL THEN NULL
        ELSE device.fcm_token
    END
WHERE device.enabled = b'1';

DROP TEMPORARY TABLE tmp_push_token_active_winners;

ALTER TABLE push_devices
    ADD CONSTRAINT chk_push_devices_active_token
        CHECK (
            (enabled = b'0' AND active_fcm_token IS NULL)
            OR
            (enabled = b'1' AND active_fcm_token = fcm_token)
        ),
    ADD CONSTRAINT uk_push_devices_active_token UNIQUE (active_fcm_token);

-- A fixed number of database rows serializes ownership transfer even when the token is new.
-- Java String.hashCode modulo 64 selects the bucket; ascending bucket locks prevent token-swap deadlocks.
CREATE TABLE push_token_lock_buckets (
    bucket_id smallint NOT NULL,
    PRIMARY KEY (bucket_id)
) engine=InnoDB;

INSERT INTO push_token_lock_buckets (bucket_id) VALUES
    (0),  (1),  (2),  (3),  (4),  (5),  (6),  (7),
    (8),  (9),  (10), (11), (12), (13), (14), (15),
    (16), (17), (18), (19), (20), (21), (22), (23),
    (24), (25), (26), (27), (28), (29), (30), (31),
    (32), (33), (34), (35), (36), (37), (38), (39),
    (40), (41), (42), (43), (44), (45), (46), (47),
    (48), (49), (50), (51), (52), (53), (54), (55),
    (56), (57), (58), (59), (60), (61), (62), (63);
