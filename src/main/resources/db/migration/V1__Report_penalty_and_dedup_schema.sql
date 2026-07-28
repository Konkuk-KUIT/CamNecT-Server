-- The existing schema is registered by Flyway as baseline version 0.
-- This migration contains every schema delta introduced by the report update.

ALTER TABLE report
    ADD COLUMN evidence_image_url VARCHAR(255) NULL,
    ADD COLUMN applied_penalty VARCHAR(30) NULL,
    ADD COLUMN target_key VARCHAR(100) NULL;

-- The old API accepted arbitrary strings. Normalize known display values and
-- safely map unknown legacy values to OTHER before Hibernate starts reading the enum.
UPDATE report
SET report_category = CASE TRIM(report_category)
    WHEN 'BUSINESS_PROMOTION' THEN 'BUSINESS_PROMOTION'
    WHEN '영업 및 홍보' THEN 'BUSINESS_PROMOTION'
    WHEN 'INSULT_DEFAMATION' THEN 'INSULT_DEFAMATION'
    WHEN '욕설 및 비방' THEN 'INSULT_DEFAMATION'
    WHEN 'FALSE_INFORMATION' THEN 'FALSE_INFORMATION'
    WHEN '허위 사실 유포' THEN 'FALSE_INFORMATION'
    WHEN 'NO_SHOW_ABANDONMENT' THEN 'NO_SHOW_ABANDONMENT'
    WHEN '노쇼 및 잠수' THEN 'NO_SHOW_ABANDONMENT'
    WHEN 'HARASSMENT_THREAT' THEN 'HARASSMENT_THREAT'
    WHEN '괴롭힘/협박' THEN 'HARASSMENT_THREAT'
    WHEN 'INAPPROPRIATE_PROFILE' THEN 'INAPPROPRIATE_PROFILE'
    WHEN '부적절한 프로필 항목' THEN 'INAPPROPRIATE_PROFILE'
    WHEN 'SEXUAL_HARASSMENT' THEN 'SEXUAL_HARASSMENT'
    WHEN '음란성 성희롱' THEN 'SEXUAL_HARASSMENT'
    WHEN 'FRAUD' THEN 'FRAUD'
    WHEN '사기 행위' THEN 'FRAUD'
    WHEN 'OTHER' THEN 'OTHER'
    WHEN '기타' THEN 'OTHER'
    ELSE 'OTHER'
END;

-- Preserve every legacy report while assigning a canonical key to the first
-- report per reporter/target. Additional historical duplicates receive a suffix.
CREATE TEMPORARY TABLE report_target_key_backfill AS
SELECT
    report_id,
    CONCAT(
        COALESCE(post_type, 'UNKNOWN'),
        ':',
        COALESCE(reported_post_id, reported_user_id, report_id)
    ) AS base_target_key,
    ROW_NUMBER() OVER (
        PARTITION BY
            reporter_id,
            COALESCE(post_type, 'UNKNOWN'),
            COALESCE(reported_post_id, reported_user_id, report_id)
        ORDER BY report_id
    ) AS duplicate_sequence
FROM report;

UPDATE report r
JOIN report_target_key_backfill b ON b.report_id = r.report_id
SET r.target_key = CASE
    WHEN b.duplicate_sequence = 1 THEN b.base_target_key
    ELSE CONCAT(b.base_target_key, ':legacy:', r.report_id)
END;

DROP TEMPORARY TABLE report_target_key_backfill;

ALTER TABLE report
    MODIFY COLUMN target_key VARCHAR(100) NOT NULL,
    ADD CONSTRAINT uk_report_reporter_target UNIQUE (reporter_id, target_key);

CREATE TABLE user_report_penalty (
    penalty_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    penalty_type VARCHAR(30) NOT NULL,
    suspension_end_date DATETIME NULL,
    reason VARCHAR(255) NULL,
    previous_status VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_user_report_penalty_report UNIQUE (report_id),
    CONSTRAINT fk_user_report_penalty_report
        FOREIGN KEY (report_id) REFERENCES report(report_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_report_penalty_user
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_report_penalty_user (user_id),
    INDEX idx_user_report_penalty_active (user_id, penalty_type, suspension_end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
