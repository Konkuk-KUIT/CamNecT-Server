package CamNecT.server.global.common.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PushTokenOwnershipMigrationContractTest {

    @Test
    void migrationRepairsDuplicatesAndEnforcesOneActiveOwner() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V8__Push_token_single_active_owner.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains(
                "COLLATE utf8mb4_bin",
                "newer_device.last_seen_at > current_device.last_seen_at",
                "newer_device.push_device_id > current_device.push_device_id",
                "SET device.enabled = CASE",
                "active_fcm_token",
                "chk_push_devices_active_token",
                "uk_push_devices_active_token UNIQUE (active_fcm_token)"
        );
    }

    @Test
    void migrationCreatesEveryBoundedCrossInstanceLockBucket() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V8__Push_token_single_active_owner.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains(
                "CREATE TABLE push_token_lock_buckets",
                "PRIMARY KEY (bucket_id)"
        );

        String seedSql = sql.substring(sql.indexOf(
                "INSERT INTO push_token_lock_buckets (bucket_id) VALUES"
        ));
        var seededBucketIds = Pattern.compile("\\((\\d+)\\)")
                .matcher(seedSql)
                .results()
                .map(result -> Integer.parseInt(result.group(1)))
                .toList();
        assertThat(seededBucketIds)
                .containsExactlyElementsOf(IntStream.range(0, 64).boxed().toList());
    }
}
