package CamNecT.server.domain.verification.email.repository;

import CamNecT.server.domain.verification.email.model.EmailVerificationToken;
import CamNecT.server.global.common.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class EmailVerificationTokenActiveInvariantTest {

    private static final String EMAIL = "user@example.com";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 26, 9, 0);

    @Autowired
    private EmailVerificationTokenRepository repository;

    @Test
    void databaseRejectsSecondActiveTokenForSameEmail() {
        repository.saveAndFlush(EmailVerificationToken.issueForEmail(EMAIL, "123456", 30, NOW));

        assertThatThrownBy(() -> repository.saveAndFlush(
                EmailVerificationToken.issueForEmail(
                        " User@Example.COM ",
                        "654321",
                        30,
                        NOW.plusSeconds(1)
                )
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usedTokenReleasesActiveEmailForReplacement() {
        EmailVerificationToken used = EmailVerificationToken.issueForEmail(EMAIL, "123456", 30, NOW);
        used.markUsed(NOW.plusSeconds(1));
        repository.saveAndFlush(used);

        EmailVerificationToken replacement = repository.saveAndFlush(
                EmailVerificationToken.issueForEmail(EMAIL, "654321", 30, NOW.plusSeconds(2))
        );

        assertThat(used.getActiveEmail()).isNull();
        assertThat(replacement.getActiveEmail()).isEqualTo("user@example.com");
    }
}
