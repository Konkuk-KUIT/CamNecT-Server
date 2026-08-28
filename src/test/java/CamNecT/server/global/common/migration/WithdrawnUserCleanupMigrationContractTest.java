package CamNecT.server.global.common.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WithdrawnUserCleanupMigrationContractTest {

    @Test
    void migrationBackfillsWithdrawnRelationshipsAndIndexesInboundFollows() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V6__Withdrawn_user_relationship_cleanup.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains(
                "DELETE utm",
                "u.status = 'WITHDRAWN'",
                "DELETE uf",
                "follower.status = 'WITHDRAWN'",
                "following_user.status = 'WITHDRAWN'",
                "CREATE INDEX idx_user_follow_following_id"
        );
    }
}
