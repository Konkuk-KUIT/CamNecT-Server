-- V1__Create_user_suspension_record_table.sql
-- Create the new UserSuspensionRecord table to store suspension and ban information
-- Initialize records for all existing users with default values

CREATE TABLE user_suspension_record (
    suspension_record_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    report_count INT NOT NULL DEFAULT 0,
    suspension_end_date DATETIME,
    is_permanently_banned BOOLEAN NOT NULL DEFAULT FALSE,
    ban_reason VARCHAR(255),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create index for faster lookups
CREATE INDEX idx_is_permanently_banned ON user_suspension_record(is_permanently_banned);
CREATE INDEX idx_suspension_end_date ON user_suspension_record(suspension_end_date);

-- Initialize suspension records for all users with default values
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
    user_id,
    0 AS report_count,
    NULL AS suspension_end_date,
    FALSE AS is_permanently_banned,
    NULL AS ban_reason,
    NOW() AS created_at,
    NOW() AS updated_at
FROM users;
