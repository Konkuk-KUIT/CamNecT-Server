package CamNecT.server.global.common.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EducationCampusMigrationContractTest {

    @Test
    void migrationPreservesLegacyRowsWhileAddingCampusForeignKey() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V12__Add_campus_to_education.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains(
                "UNIQUE (institution_id, campus_id)",
                "ADD COLUMN campus_id BIGINT NULL",
                "FOREIGN KEY (institution_id, campus_id)",
                "REFERENCES campuses (institution_id, campus_id)",
                "기존 학력은 어느 캠퍼스인지 확정할 수 없으므로 NULL을 유지한다"
        );
        assertThat(sql).doesNotContain("campus_id BIGINT NOT NULL");
    }
}
