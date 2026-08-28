ALTER TABLE campuses
    ADD CONSTRAINT uk_campuses_institution_id
        UNIQUE (institution_id, campus_id);

ALTER TABLE education
    ADD COLUMN campus_id BIGINT NULL AFTER institution_id,
    ADD CONSTRAINT fk_education_institution_campus
        FOREIGN KEY (institution_id, campus_id)
        REFERENCES campuses (institution_id, campus_id);

-- 기존 학력은 어느 캠퍼스인지 확정할 수 없으므로 NULL을 유지한다.
-- 신규/수정 요청부터 애플리케이션 계층에서 campusId를 필수로 받는다.
