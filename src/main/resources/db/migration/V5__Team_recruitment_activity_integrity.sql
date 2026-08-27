-- FK 적용 전에 기존 NULL/고아 데이터를 명시적으로 검사한다.
-- 검사가 실패하면 데이터를 수동 정리한 뒤 마이그레이션을 다시 실행해야 한다.
DROP PROCEDURE IF EXISTS assert_team_recruitment_activity_integrity;

DELIMITER $$
CREATE PROCEDURE assert_team_recruitment_activity_integrity()
BEGIN
    DECLARE invalid_count BIGINT DEFAULT 0;
    DECLARE error_message VARCHAR(128);

    SELECT COUNT(*)
      INTO invalid_count
      FROM team_recruitments
     WHERE activity_id IS NULL;

    IF invalid_count > 0 THEN
        SET error_message = CONCAT('V5 blocked: team_recruitments.activity_id NULL rows=', invalid_count);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;

    SELECT COUNT(*)
      INTO invalid_count
      FROM team_recruitments tr
      LEFT JOIN external_activities ea ON ea.activity_id = tr.activity_id
     WHERE ea.activity_id IS NULL;

    IF invalid_count > 0 THEN
        SET error_message = CONCAT('V5 blocked: orphan team_recruitments rows=', invalid_count);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;
END$$
DELIMITER ;

CALL assert_team_recruitment_activity_integrity();
DROP PROCEDURE assert_team_recruitment_activity_integrity;

ALTER TABLE team_recruitments
    MODIFY COLUMN activity_id BIGINT NOT NULL,
    ADD INDEX idx_team_recruitments_activity_id (activity_id),
    ADD CONSTRAINT fk_team_recruitments_activity
        FOREIGN KEY (activity_id) REFERENCES external_activities (activity_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT;

CREATE INDEX idx_chat_request_recruitment_status
    ON coffee_chat_request (recruitment_id, request_type, status);
