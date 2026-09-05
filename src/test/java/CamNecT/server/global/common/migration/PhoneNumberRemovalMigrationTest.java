package CamNecT.server.global.common.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Runs the exact V13 SQL against disposable H2 in MySQL mode; production MySQL
// DDL/locking and the full V0-V13 chain must also be checked on a staging copy.
class PhoneNumberRemovalMigrationTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        execute("""
                CREATE TABLE users (
                    user_id BIGINT PRIMARY KEY, username VARCHAR(50) UNIQUE,
                    password_hash VARCHAR(255), status VARCHAR(30), email VARCHAR(255) UNIQUE,
                    phone_num VARCHAR(20), CONSTRAINT legacy_phone_unique UNIQUE(phone_num)
                )
                """);
        execute("""
                CREATE TABLE gifticon_purchases (
                    purchase_id BIGINT PRIMARY KEY, user_id BIGINT REFERENCES users(user_id),
                    buyer_phone VARCHAR(30), buyer_email VARCHAR(200), recipient_phone VARCHAR(30),
                    quantity INT DEFAULT 2, total_price_points INT DEFAULT 2000,
                    export_batch_id BIGINT, exported_at TIMESTAMP, admin_success BOOLEAN
                )
                """);
        execute("CREATE TABLE point_wallet (user_id BIGINT REFERENCES users(user_id), balance INT)");
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    @Test
    void migratesExistingUsersAndOrdersWithoutGuessingGiftRecipients() throws Exception {
        String longEmail = "a".repeat(63) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(59);
        execute("INSERT INTO users VALUES (1, 'active', 'hash', 'ACTIVE', 'current@example.com', '01099998888')");
        execute("INSERT INTO users VALUES (2, 'withdrawn', 'old-hash', 'WITHDRAWN', NULL, NULL)");
        try (var statement = connection.prepareStatement("INSERT INTO users VALUES (3, 'fallback', 'hash', 'ACTIVE', ?, '01055556666')")) {
            statement.setString(1, longEmail);
            statement.executeUpdate();
        }
        execute("INSERT INTO point_wallet VALUES (1, 4321)");
        execute("""
                INSERT INTO gifticon_purchases (purchase_id, user_id, buyer_phone, buyer_email, recipient_phone) VALUES
                    (1, 1, '01011112222', 'original@example.com', NULL),
                    (2, 1, '01011112222', 'original@example.com', '010-1111-2222'),
                    (3, 1, '01011112222', 'original@example.com', '01077778888'),
                    (4, 3, NULL, ' ', '010 5555 6666'),
                    (5, 2, NULL, NULL, NULL),
                    (6, 2, '01033334444', 'withdrawn-snapshot@example.com', '01033334444'),
                    (7, 1, '01011112222', 'original@example.com', '01077778888'),
                    (8, 1, '01011112222', 'original@example.com', ' '),
                    (9, 1, '01011112222', 'original@example.com', '01099998888')
                """);
        execute("UPDATE gifticon_purchases SET export_batch_id = 42, exported_at = '2026-09-01 12:00:00', admin_success = TRUE WHERE purchase_id = 7");

        migrate();

        assertThat(email(1)).isEqualTo("original@example.com");
        assertThat(email(2)).isEqualTo("original@example.com");
        assertThat(email(3)).isNull();
        assertThat(email(4)).isEqualTo(longEmail);
        assertThat(email(5)).isNull();
        assertThat(email(6)).isEqualTo("withdrawn-snapshot@example.com");
        assertThat(email(7)).isNull();
        assertThat(email(8)).isEqualTo("original@example.com");
        // Current account phone does not override a different purchase-time snapshot.
        assertThat(email(9)).isNull();
        assertThat(scalar("SELECT COUNT(*) FROM users")).isEqualTo("3");
        assertThat(scalar("SELECT password_hash FROM users WHERE user_id = 1")).isEqualTo("hash");
        assertThat(scalar("SELECT status FROM users WHERE user_id = 2")).isEqualTo("WITHDRAWN");
        assertThat(scalar("SELECT balance FROM point_wallet WHERE user_id = 1")).isEqualTo("4321");
        assertThat(scalar("SELECT COUNT(*) FROM gifticon_purchases")).isEqualTo("9");
        assertThat(scalar("SELECT SUM(total_price_points) FROM gifticon_purchases")).isEqualTo("18000");
        assertThat(scalar("SELECT export_batch_id FROM gifticon_purchases WHERE purchase_id = 7")).isEqualTo("42");
        assertThat(scalar("SELECT exported_at FROM gifticon_purchases WHERE purchase_id = 7")).startsWith("2026-09-01 12:00:00");
        assertThat(scalar("SELECT admin_success FROM gifticon_purchases WHERE purchase_id = 7")).isEqualTo("TRUE");
        assertPhoneColumnsRemoved();
    }

    @Test
    void migratesEmptySchemaAndAllowsEmailOnlyUsersAndLongEmailSnapshots() throws Exception {
        migrate();
        execute("INSERT INTO users (user_id, username, password_hash, status, email) VALUES (1, 'new-user', 'hash', 'ACTIVE', 'new@example.com')");
        try (var statement = connection.prepareStatement("INSERT INTO gifticon_purchases (purchase_id, user_id, buyer_email, recipient_email) VALUES (1, 1, ?, ?)")) {
            statement.setString(1, "a".repeat(255));
            statement.setString(2, "b".repeat(255));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        assertPhoneColumnsRemoved();
    }

    private void migrate() {
        ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migration/V13__Remove_phone_numbers_and_use_gifticon_email.sql"));
    }

    private void assertPhoneColumnsRemoved() throws Exception {
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.columns WHERE column_name IN ('PHONE_NUM', 'BUYER_PHONE', 'RECIPIENT_PHONE')"))
                .isEqualTo("0");
    }

    private String email(long id) throws Exception {
        return scalar("SELECT recipient_email FROM gifticon_purchases WHERE purchase_id = " + id);
    }

    private String scalar(String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private void execute(String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
