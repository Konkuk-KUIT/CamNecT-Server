package CamNecT.server.global.common.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationIssuanceMigrationContractTest {

    @Test
    void migrationKeepsNewestLegacyTokenAndEnforcesOneActiveEmail() throws Exception {
        String sql = migrationSql();

        assertThat(sql).contains(
                "newer.id > stale.id",
                "DELETE stale",
                "LOWER(TRIM(newer.email))",
                "COLLATE utf8mb4_bin",
                "active_email",
                "WHEN used_at IS NULL THEN LOWER(TRIM(email))",
                "uk_email_verification_active_email UNIQUE (active_email)",
                "idx_evt_email_used_id (email, used_at, id)"
        );
    }

    @Test
    void migrationCreatesEveryBoundedCrossInstanceLockBucket() throws Exception {
        String sql = migrationSql();

        assertThat(sql).contains(
                "CREATE TABLE email_verification_lock_buckets",
                "PRIMARY KEY (bucket_id)"
        );

        String seedSql = sql.substring(sql.indexOf(
                "INSERT INTO email_verification_lock_buckets (bucket_id) VALUES"
        ));
        var seededBucketIds = Pattern.compile("\\((\\d+)\\)")
                .matcher(seedSql)
                .results()
                .map(result -> Integer.parseInt(result.group(1)))
                .toList();
        assertThat(seededBucketIds)
                .containsExactlyElementsOf(IntStream.range(0, 64).boxed().toList());
    }

    private String migrationSql() throws Exception {
        return new ClassPathResource(
                "db/migration/V11__Email_verification_single_active_code.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
    }
}
