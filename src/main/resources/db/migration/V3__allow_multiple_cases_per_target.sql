-- 동일 대상에 대한 신고 처리 완료 후 재신고를 허용하기 위해
-- report_case 테이블의 target_key 전체 유니크 제약을 제거하고,
-- RECEIVED 상태에서만 단일 활성 케이스를 보장하는 부분 유니크 가드를 추가합니다.
--
-- received_guard: status = 'RECEIVED'이면 1, 그 외는 NULL (STORED generated column)
-- MySQL에서 NULL은 유니크 제약 중복으로 처리되지 않으므로,
-- uk_report_case_target_received는 RECEIVED 케이스에 대해서만 (target_key) 유니크를 보장합니다.
ALTER TABLE report_case DROP INDEX uk_report_case_target;

ALTER TABLE report_case
    ADD COLUMN received_guard TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'RECEIVED' THEN 1 ELSE NULL END
    ) STORED,
    ADD UNIQUE INDEX uk_report_case_target_received (target_key, received_guard),
    ADD INDEX idx_report_case_target_status (target_key, status);
