package CamNecT.server.global.common.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TeamRecruitmentMigrationContractTest {

    @Test
    void migrationChecksExistingDataBeforeAddingRestrictFk() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V5__Team_recruitment_activity_integrity.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        int nullCheck = sql.indexOf("WHERE activity_id IS NULL");
        int orphanCheck = sql.indexOf("LEFT JOIN external_activities");
        int alterTable = sql.indexOf("ALTER TABLE team_recruitments");

        assertThat(nullCheck).isGreaterThanOrEqualTo(0).isLessThan(alterTable);
        assertThat(orphanCheck).isGreaterThanOrEqualTo(0).isLessThan(alterTable);
        assertThat(sql).contains(
                "MODIFY COLUMN activity_id BIGINT NOT NULL",
                "FOREIGN KEY (activity_id) REFERENCES external_activities (activity_id)",
                "ON DELETE RESTRICT",
                "idx_chat_request_recruitment_status"
        );
    }
}
