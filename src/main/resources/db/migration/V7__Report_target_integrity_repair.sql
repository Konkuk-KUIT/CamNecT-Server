-- Report rows created before server-side target resolution trusted reported_user_id
-- from the client. Preserve every suspicious original row for operational review,
-- repair only cases whose owner can be derived unambiguously, and quarantine the
-- rest from automatic approval.

DROP TEMPORARY TABLE IF EXISTS report_case_owner_resolution;

CREATE TEMPORARY TABLE report_case_owner_resolution AS
SELECT
    c.case_id,
    c.target_type,
    c.target_id,
    CASE c.target_type
        WHEN 'COMMUNITY' THEN p.user_id
        WHEN 'COMMUNITY_COMMENT' THEN cm.user_id
        WHEN 'ACTIVITY' THEN a.user_id
        WHEN 'ACTIVITY_RECRUITMENT' THEN tr.user_id
        WHEN 'USER' THEN target_user.user_id
        WHEN 'CHAT' THEN chat_owner.candidate_user_id
        ELSE NULL
    END AS candidate_user_id,
    CASE c.target_type
        WHEN 'COMMUNITY' THEN
            CASE WHEN p.post_id IS NULL THEN 'TARGET_MISSING' ELSE 'RESOLVED' END
        WHEN 'COMMUNITY_COMMENT' THEN
            CASE WHEN cm.comment_id IS NULL THEN 'TARGET_MISSING' ELSE 'RESOLVED' END
        WHEN 'ACTIVITY' THEN
            CASE
                WHEN a.activity_id IS NULL THEN 'TARGET_MISSING'
                WHEN a.user_id IS NULL THEN 'TARGET_UNOWNED'
                ELSE 'RESOLVED'
            END
        WHEN 'ACTIVITY_RECRUITMENT' THEN
            CASE
                WHEN tr.recruit_id IS NULL THEN 'TARGET_MISSING'
                WHEN tr.user_id IS NULL THEN 'TARGET_UNOWNED'
                ELSE 'RESOLVED'
            END
        WHEN 'USER' THEN
            CASE WHEN target_user.user_id IS NULL THEN 'TARGET_MISSING' ELSE 'RESOLVED' END
        WHEN 'CHAT' THEN COALESCE(chat_owner.resolution, 'TARGET_MISSING')
        ELSE 'UNSUPPORTED_TARGET_TYPE'
    END AS resolution
FROM report_case c
LEFT JOIN posts p
    ON c.target_type = 'COMMUNITY'
    AND p.post_id = c.target_id
LEFT JOIN comments cm
    ON c.target_type = 'COMMUNITY_COMMENT'
    AND cm.comment_id = c.target_id
LEFT JOIN external_activities a
    ON c.target_type = 'ACTIVITY'
    AND a.activity_id = c.target_id
LEFT JOIN team_recruitments tr
    ON c.target_type = 'ACTIVITY_RECRUITMENT'
    AND tr.recruit_id = c.target_id
LEFT JOIN users target_user
    ON c.target_type = 'USER'
    AND target_user.user_id = c.target_id
LEFT JOIN (
    SELECT
        chat_case.case_id,
        CASE
            WHEN thread.cc_thread_id IS NULL THEN NULL
            WHEN COUNT(r.report_id) = 0 THEN NULL
            WHEN SUM(CASE
                WHEN r.reporter_id = thread.requester_id OR r.reporter_id = thread.receiver_id THEN 1
                ELSE 0
            END) <> COUNT(r.report_id) THEN NULL
            WHEN COUNT(DISTINCT CASE
                WHEN r.reporter_id = thread.requester_id THEN thread.receiver_id
                WHEN r.reporter_id = thread.receiver_id THEN thread.requester_id
                ELSE NULL
            END) <> 1 THEN NULL
            ELSE MIN(CASE
                WHEN r.reporter_id = thread.requester_id THEN thread.receiver_id
                WHEN r.reporter_id = thread.receiver_id THEN thread.requester_id
                ELSE NULL
            END)
        END AS candidate_user_id,
        CASE
            WHEN thread.cc_thread_id IS NULL THEN 'TARGET_MISSING'
            WHEN COUNT(r.report_id) = 0 THEN 'NO_SUBMISSIONS'
            WHEN SUM(CASE
                WHEN r.reporter_id = thread.requester_id OR r.reporter_id = thread.receiver_id THEN 1
                ELSE 0
            END) <> COUNT(r.report_id) THEN 'CHAT_INVALID_REPORTER'
            WHEN COUNT(DISTINCT CASE
                WHEN r.reporter_id = thread.requester_id THEN thread.receiver_id
                WHEN r.reporter_id = thread.receiver_id THEN thread.requester_id
                ELSE NULL
            END) <> 1 THEN 'CHAT_AMBIGUOUS_OWNER'
            ELSE 'RESOLVED'
        END AS resolution
    FROM report_case chat_case
    LEFT JOIN coffee_chat_thread thread
        ON thread.cc_thread_id = chat_case.target_id
    LEFT JOIN report r
        ON r.case_id = chat_case.case_id
    WHERE chat_case.target_type = 'CHAT'
    GROUP BY
        chat_case.case_id,
        thread.cc_thread_id,
        thread.requester_id,
        thread.receiver_id
) chat_owner
    ON chat_owner.case_id = c.case_id;

ALTER TABLE report_case_owner_resolution
    ADD COLUMN authoritative_user_id BIGINT NULL,
    ADD COLUMN canonical_target_key VARCHAR(100) NULL,
    ADD COLUMN submission_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN structural_mismatch_count BIGINT NOT NULL DEFAULT 0;

-- Scalar author columns in some legacy tables were not consistently protected by
-- foreign keys. A missing users row is not safe to repair into report_case.
UPDATE report_case_owner_resolution resolution
LEFT JOIN users authoritative_user
    ON authoritative_user.user_id = resolution.candidate_user_id
SET
    resolution.authoritative_user_id = authoritative_user.user_id,
    resolution.resolution = CASE
        WHEN resolution.candidate_user_id IS NOT NULL AND authoritative_user.user_id IS NULL
            THEN 'OWNER_USER_MISSING'
        ELSE resolution.resolution
    END;

UPDATE report_case_owner_resolution
SET canonical_target_key = CASE
    WHEN authoritative_user_id IS NULL THEN NULL
    WHEN target_type = 'CHAT' THEN CONCAT('CHAT:', target_id, ':', authoritative_user_id)
    ELSE CONCAT(target_type, ':', target_id)
END;

UPDATE report_case_owner_resolution resolution
JOIN (
    SELECT
        c.case_id,
        COUNT(r.report_id) AS submission_count,
        COALESCE(SUM(CASE
            WHEN r.report_id IS NULL THEN 0
            WHEN NOT (r.post_type <=> c.target_type) THEN 1
            WHEN NOT (r.target_key <=> c.target_key) THEN 1
            WHEN c.target_type = 'USER' AND r.reported_post_id IS NOT NULL THEN 1
            WHEN c.target_type <> 'USER' AND NOT (r.reported_post_id <=> c.target_id) THEN 1
            ELSE 0
        END), 0) AS structural_mismatch_count
    FROM report_case c
    LEFT JOIN report r ON r.case_id = c.case_id
    GROUP BY c.case_id
) submission_integrity
    ON submission_integrity.case_id = resolution.case_id
SET
    resolution.submission_count = submission_integrity.submission_count,
    resolution.structural_mismatch_count = submission_integrity.structural_mismatch_count;

CREATE TABLE IF NOT EXISTS report_target_integrity_audit (
    audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    audit_subject_key VARCHAR(80) NOT NULL,
    case_id BIGINT NOT NULL,
    report_id BIGINT NULL,
    case_status VARCHAR(20) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NOT NULL,
    original_target_key VARCHAR(100) NOT NULL,
    original_case_reported_user_id BIGINT NOT NULL,
    original_submission_reported_user_id BIGINT NULL,
    reporter_id BIGINT NULL,
    authoritative_user_id BIGINT NULL,
    penalty_user_id BIGINT NULL,
    finding VARCHAR(50) NOT NULL,
    remediation VARCHAR(40) NOT NULL,
    processed_by_admin_id BIGINT NULL,
    processed_at DATETIME(6) NULL,
    detected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_report_target_integrity_subject UNIQUE (case_id, audit_subject_key),
    INDEX idx_report_target_integrity_case (case_id),
    INDEX idx_report_target_integrity_remediation (remediation, case_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO report_target_integrity_audit (
    audit_subject_key,
    case_id,
    report_id,
    case_status,
    target_type,
    target_id,
    original_target_key,
    original_case_reported_user_id,
    original_submission_reported_user_id,
    reporter_id,
    authoritative_user_id,
    penalty_user_id,
    finding,
    remediation,
    processed_by_admin_id,
    processed_at
)
SELECT
    CASE
        WHEN r.report_id IS NULL THEN CONCAT('CASE:', c.case_id)
        ELSE CONCAT('REPORT:', r.report_id)
    END,
    c.case_id,
    r.report_id,
    c.status,
    c.target_type,
    c.target_id,
    c.target_key,
    c.reported_user_id,
    r.reported_user_id,
    r.reporter_id,
    resolution.authoritative_user_id,
    penalty.user_id,
    CASE
        WHEN resolution.resolution <> 'RESOLVED' THEN resolution.resolution
        WHEN NOT (c.target_key <=> resolution.canonical_target_key) THEN 'TARGET_KEY_MISMATCH'
        WHEN resolution.submission_count = 0 THEN 'NO_SUBMISSIONS'
        WHEN resolution.structural_mismatch_count > 0 THEN 'SUBMISSION_TARGET_MISMATCH'
        WHEN penalty.penalty_id IS NOT NULL
             AND penalty.user_id <> resolution.authoritative_user_id THEN 'PENALTY_OWNER_MISMATCH'
        WHEN c.reported_user_id <> resolution.authoritative_user_id THEN 'CASE_OWNER_MISMATCH'
        WHEN r.reported_user_id <> resolution.authoritative_user_id THEN 'SUBMISSION_OWNER_MISMATCH'
        WHEN c.status = 'RECEIVED' AND penalty.penalty_id IS NOT NULL
            THEN 'UNEXPECTED_OPEN_CASE_PENALTY'
        ELSE 'UNKNOWN_MISMATCH'
    END,
    CASE
        WHEN c.status = 'RECEIVED'
             AND resolution.resolution = 'RESOLVED'
             AND c.target_key <=> resolution.canonical_target_key
             AND resolution.submission_count > 0
             AND resolution.structural_mismatch_count = 0
             AND penalty.penalty_id IS NULL
            THEN 'REPAIR_OPEN_CASE'
        WHEN c.status = 'RECEIVED' THEN 'QUARANTINE_OPEN_CASE'
        ELSE 'AUDIT_PROCESSED_CASE'
    END,
    c.processed_by_admin_id,
    c.processed_at
FROM report_case c
JOIN report_case_owner_resolution resolution
    ON resolution.case_id = c.case_id
LEFT JOIN report r
    ON r.case_id = c.case_id
LEFT JOIN user_report_penalty penalty
    ON penalty.case_id = c.case_id
WHERE resolution.resolution <> 'RESOLVED'
   OR NOT (c.target_key <=> resolution.canonical_target_key)
   OR resolution.submission_count = 0
   OR resolution.structural_mismatch_count > 0
   OR c.reported_user_id <> resolution.authoritative_user_id
   OR r.reported_user_id <> resolution.authoritative_user_id
   OR (penalty.penalty_id IS NOT NULL AND penalty.user_id <> resolution.authoritative_user_id)
   OR (c.status = 'RECEIVED' AND penalty.penalty_id IS NOT NULL)
ON DUPLICATE KEY UPDATE audit_id = audit_id;

-- A repair is safe only when the target record, canonical case key, and every
-- submission's target coordinates agree. Only the untrusted owner IDs change.
UPDATE report_case c
JOIN report_case_owner_resolution resolution
    ON resolution.case_id = c.case_id
SET c.reported_user_id = resolution.authoritative_user_id
WHERE c.status = 'RECEIVED'
  AND resolution.resolution = 'RESOLVED'
  AND c.target_key <=> resolution.canonical_target_key
  AND resolution.submission_count > 0
  AND resolution.structural_mismatch_count = 0
  AND NOT EXISTS (
      SELECT 1
      FROM user_report_penalty existing_penalty
      WHERE existing_penalty.case_id = c.case_id
  );

UPDATE report r
JOIN report_case c
    ON c.case_id = r.case_id
JOIN report_case_owner_resolution resolution
    ON resolution.case_id = c.case_id
SET r.reported_user_id = resolution.authoritative_user_id
WHERE c.status = 'RECEIVED'
  AND resolution.resolution = 'RESOLVED'
  AND c.target_key <=> resolution.canonical_target_key
  AND resolution.submission_count > 0
  AND resolution.structural_mismatch_count = 0
  AND NOT EXISTS (
      SELECT 1
      FROM user_report_penalty existing_penalty
      WHERE existing_penalty.case_id = c.case_id
  );

-- Keep unresolved cases RECEIVED so an administrator can reject them, but make
-- the quarantine explicit. Runtime approval independently revalidates the owner.
UPDATE report_case c
JOIN report_case_owner_resolution resolution
    ON resolution.case_id = c.case_id
SET c.moderation_reason = CASE
    WHEN c.moderation_reason LIKE '[TARGET_INTEGRITY_QUARANTINED]%' THEN c.moderation_reason
    ELSE LEFT(CONCAT(
        '[TARGET_INTEGRITY_QUARANTINED] ',
        CASE
            WHEN resolution.resolution <> 'RESOLVED' THEN resolution.resolution
            WHEN NOT (c.target_key <=> resolution.canonical_target_key) THEN 'TARGET_KEY_MISMATCH'
            WHEN resolution.submission_count = 0 THEN 'NO_SUBMISSIONS'
            WHEN EXISTS (
                SELECT 1
                FROM user_report_penalty existing_penalty
                WHERE existing_penalty.case_id = c.case_id
            ) THEN 'UNEXPECTED_OPEN_CASE_PENALTY'
            ELSE 'SUBMISSION_TARGET_MISMATCH'
        END,
        CASE
            WHEN c.moderation_reason IS NULL OR c.moderation_reason = '' THEN ''
            ELSE CONCAT(' | ', c.moderation_reason)
        END
    ), 500)
END
WHERE c.status = 'RECEIVED'
  AND NOT (
      resolution.resolution = 'RESOLVED'
      AND c.target_key <=> resolution.canonical_target_key
      AND resolution.submission_count > 0
      AND resolution.structural_mismatch_count = 0
      AND NOT EXISTS (
          SELECT 1
          FROM user_report_penalty existing_penalty
          WHERE existing_penalty.case_id = c.case_id
      )
  );

DROP TEMPORARY TABLE report_case_owner_resolution;
