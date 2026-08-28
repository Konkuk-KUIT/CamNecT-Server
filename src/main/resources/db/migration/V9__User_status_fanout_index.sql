-- Keyset fanout scans active users by ascending primary key without an offset scan.
ALTER TABLE users
    ADD INDEX idx_users_status_user_id (status, user_id);
