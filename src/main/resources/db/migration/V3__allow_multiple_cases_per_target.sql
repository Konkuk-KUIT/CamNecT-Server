-- 동일 대상에 대한 신고 처리 완료 후 재신고를 허용하기 위해
-- report_case 테이블의 target_key 유니크 제약을 제거합니다.
-- 이후 활성 케이스(RECEIVED) 조회는 애플리케이션 레벨에서 status 조건으로 관리합니다.
ALTER TABLE report_case DROP INDEX uk_report_case_target;
