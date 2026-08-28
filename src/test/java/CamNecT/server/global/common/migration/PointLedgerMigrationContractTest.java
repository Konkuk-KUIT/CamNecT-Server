package CamNecT.server.global.common.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PointLedgerMigrationContractTest {

    @Test
    void initialSchemaProvidesTheUniqueKeysRequiredForWalletAndEventSerialization() throws Exception {
        String sql = new ClassPathResource("db/migration/V0__Initial_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("event_key varchar(64)")
                .containsPattern("(?i)alter table point_wallet add constraint \\S+ unique \\(user_id\\)")
                .containsPattern("(?i)alter table point_transaction add constraint \\S+ unique \\(event_key\\)");
    }
}
