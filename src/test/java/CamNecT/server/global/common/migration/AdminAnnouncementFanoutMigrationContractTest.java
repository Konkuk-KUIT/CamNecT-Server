package CamNecT.server.global.common.migration;

import CamNecT.server.domain.users.model.Users;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAnnouncementFanoutMigrationContractTest {

    @Test
    void migrationAndEntityDeclareTheStatusUserIdFanoutIndex() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V9__User_status_fanout_index.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains(
                "ALTER TABLE users",
                "ADD INDEX idx_users_status_user_id (status, user_id)"
        );

        Table table = Users.class.getAnnotation(Table.class);
        assertThat(table.indexes()).anySatisfy(index -> {
            assertThat(index.name()).isEqualTo("idx_users_status_user_id");
            assertThat(index.columnList()).isEqualTo("status,user_id");
        });
    }
}
