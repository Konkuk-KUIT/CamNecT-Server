ALTER TABLE institutions
    MODIFY COLUMN institution_name_eng VARCHAR(100) NULL,
    ADD COLUMN university_type VARCHAR(50) NULL AFTER institution_name_eng,
    ADD COLUMN primary_region VARCHAR(50) NULL AFTER university_type,
    ADD COLUMN source_as_of_date DATE NULL AFTER primary_region,
    ADD COLUMN source_url VARCHAR(512) NULL AFTER source_as_of_date;

SET @drop_legacy_institution_name_eng = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE institutions DROP COLUMN institutiton_name_eng',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'institutions'
      AND column_name = 'institutiton_name_eng'
);

PREPARE drop_legacy_institution_name_eng
    FROM @drop_legacy_institution_name_eng;
EXECUTE drop_legacy_institution_name_eng;
DEALLOCATE PREPARE drop_legacy_institution_name_eng;

ALTER TABLE institutions
    ADD CONSTRAINT uk_institutions_code UNIQUE (institution_code),
    ADD CONSTRAINT uk_institutions_name_kor UNIQUE (institution_name_kor);

CREATE TABLE campuses (
    campus_id BIGINT NOT NULL AUTO_INCREMENT,
    institution_id BIGINT NOT NULL,
    campus_name VARCHAR(100) NOT NULL,
    full_campus_name VARCHAR(150) NOT NULL,
    campus_relation VARCHAR(50) NOT NULL,
    campus_order INT NOT NULL,
    region VARCHAR(50) NOT NULL,
    source_as_of_date DATE NOT NULL,
    source_url VARCHAR(512) NOT NULL,
    is_active BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (campus_id),
    CONSTRAINT uk_campuses_institution_name UNIQUE (institution_id, campus_name),
    CONSTRAINT uk_campuses_institution_order UNIQUE (institution_id, campus_order),
    CONSTRAINT chk_campuses_order_positive CHECK (campus_order > 0),
    CONSTRAINT fk_campuses_institution
        FOREIGN KEY (institution_id) REFERENCES institutions(institution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
