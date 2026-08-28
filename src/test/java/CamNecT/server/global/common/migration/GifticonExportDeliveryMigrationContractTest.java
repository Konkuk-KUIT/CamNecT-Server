package CamNecT.server.global.common.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GifticonExportDeliveryMigrationContractTest {

    @Test
    void migrationBackfillsLegacyRowsWithoutSchedulingAutomaticResend() throws Exception {
        String sql = migrationSql();

        assertThat(sql).contains(
                "ADD COLUMN delivery_status VARCHAR(32) NULL",
                "ADD COLUMN delivery_attempt_count INT NOT NULL DEFAULT 0",
                "SET delivery_status = 'LEGACY_UNKNOWN'",
                "WHERE delivery_status IS NULL",
                "MODIFY COLUMN delivery_status VARCHAR(32) NOT NULL",
                "CHECK (delivery_status IN ('READY', 'SUBMITTED', 'FAILED', 'LEGACY_UNKNOWN'))"
        );
    }

    @Test
    void migrationAddsDueAndDeterministicPurchaseQueueIndexes() throws Exception {
        String sql = migrationSql().replaceAll("\\s+", " ");

        assertThat(sql).contains(
                "idx_gifticon_export_delivery_due (delivery_status, next_attempt_at, export_batch_id)",
                "DROP INDEX idx_gifticon_purchase_export",
                "idx_gifticon_purchase_export_queue (export_batch_id, requested_at, purchase_id)"
        );
    }

    private String migrationSql() throws Exception {
        return new ClassPathResource(
                "db/migration/V10__Gifticon_export_delivery_outbox.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
    }
}
