package CamNecT.server.global.common.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTargetIntegrityMigrationContractTest {

    @Test
    void migrationRepairsOnlySafeOpenCasesAndAuditsTheRest() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V7__Report_target_integrity_repair.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        int caseRepairStart = sql.indexOf("UPDATE report_case c");
        int submissionRepairStart = sql.indexOf("UPDATE report r", caseRepairStart);
        int quarantineStart = sql.lastIndexOf("UPDATE report_case c");

        assertThat(caseRepairStart).isGreaterThanOrEqualTo(0);
        assertThat(submissionRepairStart).isGreaterThan(caseRepairStart);
        assertThat(quarantineStart).isGreaterThan(submissionRepairStart);

        String caseRepair = sql.substring(caseRepairStart, submissionRepairStart);
        String submissionRepair = sql.substring(submissionRepairStart, quarantineStart);
        String quarantine = sql.substring(quarantineStart);

        assertThat(sql).contains(
                "DROP TEMPORARY TABLE IF EXISTS report_case_owner_resolution",
                "CREATE TABLE IF NOT EXISTS report_target_integrity_audit",
                "CONSTRAINT uk_report_target_integrity_subject UNIQUE",
                "INSERT INTO report_target_integrity_audit",
                "ON DUPLICATE KEY UPDATE audit_id = audit_id",
                "'AUDIT_PROCESSED_CASE'",
                "'UNEXPECTED_OPEN_CASE_PENALTY'",
                "c.status = 'RECEIVED' AND penalty.penalty_id IS NOT NULL"
        );
        assertSafeRepair(caseRepair);
        assertSafeRepair(submissionRepair);
        assertThat(quarantine).contains(
                "c.status = 'RECEIVED'",
                "[TARGET_INTEGRITY_QUARANTINED]",
                "UNEXPECTED_OPEN_CASE_PENALTY",
                "NOT EXISTS",
                "FROM user_report_penalty existing_penalty",
                "c.moderation_reason LIKE '[TARGET_INTEGRITY_QUARANTINED]%'"
        );
    }

    private static void assertSafeRepair(String repairSql) {
        assertThat(repairSql).contains(
                "c.status = 'RECEIVED'",
                "resolution.resolution = 'RESOLVED'",
                "resolution.submission_count > 0",
                "resolution.structural_mismatch_count = 0",
                "NOT EXISTS",
                "FROM user_report_penalty existing_penalty",
                "existing_penalty.case_id = c.case_id"
        );
    }
}
