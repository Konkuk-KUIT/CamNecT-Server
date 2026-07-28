-- The existing schema is registered by Flyway as baseline version 0.
-- This migration contains the report-domain schema delta for this rollout.

ALTER TABLE report
    ADD COLUMN evidence_image_url VARCHAR(255) NULL,
    ADD COLUMN applied_penalty VARCHAR(30) NULL,
    ADD COLUMN target_key VARCHAR(100) NULL,
    ADD COLUMN case_id BIGINT NULL,
    ADD COLUMN submission_slot BIGINT NOT NULL DEFAULT 0,
    MODIFY COLUMN post_type VARCHAR(40) NULL,
    MODIFY COLUMN report_category VARCHAR(40) NOT NULL,
    MODIFY COLUMN status VARCHAR(20) NOT NULL;

-- Normalize legacy display values to enum names.
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

CREATE TEMPORARY TABLE report_target_backfill AS
SELECT
    report_id,
    CONCAT(
        COALESCE(post_type, 'UNKNOWN'),
        ':',
        CASE
            WHEN post_type = 'USER' THEN COALESCE(reported_user_id, report_id)
            ELSE COALESCE(reported_post_id, reported_user_id, report_id)
        END
    ) AS base_target_key,
    ROW_NUMBER() OVER (
        PARTITION BY
            reporter_id,
            COALESCE(post_type, 'UNKNOWN'),
            CASE
                WHEN post_type = 'USER' THEN COALESCE(reported_user_id, report_id)
                ELSE COALESCE(reported_post_id, reported_user_id, report_id)
            END
        ORDER BY report_id
    ) AS reporter_duplicate_sequence
FROM report;

UPDATE report r
JOIN report_target_backfill b ON b.report_id = r.report_id
SET
    r.target_key = b.base_target_key,
    r.submission_slot = CASE
        WHEN b.reporter_duplicate_sequence = 1 THEN 0
        ELSE r.report_id
    END;

CREATE TABLE report_case (
    case_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_key VARCHAR(100) NOT NULL,
    reported_user_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    report_count BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    decided_category VARCHAR(40) NULL,
    applied_penalty VARCHAR(30) NULL,
    moderation_reason VARCHAR(500) NULL,
    processed_by_admin_id BIGINT NULL,
    processed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_report_case_target UNIQUE (target_key),
    CONSTRAINT fk_report_case_reported_user
        FOREIGN KEY (reported_user_id) REFERENCES users(user_id),
    INDEX idx_report_case_filter (target_type, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO report_case (
    target_key,
    reported_user_id,
    target_id,
    target_type,
    report_count,
    status,
    decided_category,
    applied_penalty,
    processed_at,
    created_at,
    updated_at
)
SELECT
    grouped.base_target_key,
    representative.reported_user_id,
    CASE
        WHEN representative.post_type = 'USER' THEN representative.reported_user_id
        ELSE COALESCE(representative.reported_post_id, representative.reported_user_id)
    END,
    COALESCE(representative.post_type, 'USER'),
    grouped.report_count,
    grouped.case_status,
    CASE WHEN grouped.case_status = 'RESOLVED' THEN representative.report_category ELSE NULL END,
    representative.applied_penalty,
    CASE WHEN grouped.case_status = 'RECEIVED' THEN NULL ELSE grouped.updated_at END,
    grouped.created_at,
    grouped.updated_at
FROM (
    SELECT
        b.base_target_key,
        MIN(b.report_id) AS representative_report_id,
        COUNT(*) AS report_count,
        CASE
            WHEN SUM(r.status = 'RECEIVED') > 0 THEN 'RECEIVED'
            WHEN SUM(r.status = 'RESOLVED') > 0 THEN 'RESOLVED'
            ELSE 'REJECTED'
        END AS case_status,
        MIN(r.created_at) AS created_at,
        MAX(r.updated_at) AS updated_at
    FROM report_target_backfill b
    JOIN report r ON r.report_id = b.report_id
    GROUP BY b.base_target_key
) grouped
JOIN report representative ON representative.report_id = grouped.representative_report_id;

UPDATE report r
JOIN report_case c ON c.target_key = r.target_key
SET r.case_id = c.case_id;

DROP TEMPORARY TABLE report_target_backfill;

ALTER TABLE report
    MODIFY COLUMN target_key VARCHAR(100) NOT NULL,
    MODIFY COLUMN case_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_report_report_case
        FOREIGN KEY (case_id) REFERENCES report_case(case_id),
    ADD CONSTRAINT uk_report_reporter_case_slot
        UNIQUE (reporter_id, case_id, submission_slot),
    ADD INDEX idx_report_case_created (case_id, created_at);

CREATE TABLE user_report_penalty (
    penalty_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    penalty_type VARCHAR(30) NOT NULL,
    suspension_end_date DATETIME NULL,
    reason VARCHAR(255) NULL,
    previous_status VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_user_report_penalty_case UNIQUE (case_id),
    CONSTRAINT fk_user_report_penalty_case
        FOREIGN KEY (case_id) REFERENCES report_case(case_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_report_penalty_user
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_report_penalty_user (user_id),
    INDEX idx_user_report_penalty_active (user_id, penalty_type, suspension_end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
