CREATE TABLE report_evidence (
    evidence_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_report_evidence_order UNIQUE (report_id, sort_order),
    CONSTRAINT fk_report_evidence_report
        FOREIGN KEY (report_id) REFERENCES report(report_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO report_evidence (
    report_id,
    storage_key,
    original_filename,
    content_type,
    file_size,
    sort_order,
    created_at
)
SELECT
    report_id,
    evidence_image_url,
    NULL,
    'application/octet-stream',
    NULL,
    0,
    created_at
FROM report
WHERE evidence_image_url IS NOT NULL
  AND TRIM(evidence_image_url) <> '';

ALTER TABLE report
    DROP COLUMN evidence_image_url;
