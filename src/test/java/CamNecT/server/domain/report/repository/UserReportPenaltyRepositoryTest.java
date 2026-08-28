package CamNecT.server.domain.report.repository;

import CamNecT.server.domain.report.model.PenaltyType;
import CamNecT.server.domain.report.model.ReportCase;
import CamNecT.server.domain.report.model.TargetType;
import CamNecT.server.domain.report.model.UserReportPenalty;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class UserReportPenaltyRepositoryTest {

    @Autowired
    private UserReportPenaltyRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void activeRestrictionQueryCombinesPermanentAndUnexpiredSuspensions() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 0);
        Users unrestricted = persistUser("unrestricted");
        Users temporarilyRestricted = persistUser("temporary");
        Users permanentlyRestricted = persistUser("permanent");

        entityManager.persist(UserReportPenalty.warning(
                persistCase(unrestricted, "USER:" + unrestricted.getUserId()),
                unrestricted,
                "warning"
        ));
        entityManager.persist(UserReportPenalty.suspended(
                persistCase(unrestricted, "LEGACY_EXPIRED:" + unrestricted.getUserId()),
                unrestricted,
                now,
                "expired at boundary"
        ));
        entityManager.persist(UserReportPenalty.suspended(
                persistCase(temporarilyRestricted, "USER:" + temporarilyRestricted.getUserId()),
                temporarilyRestricted,
                now.plusSeconds(1),
                "active suspension"
        ));
        entityManager.persist(UserReportPenalty.permanentlyBanned(
                persistCase(permanentlyRestricted, "USER:" + permanentlyRestricted.getUserId()),
                permanentlyRestricted,
                "permanent ban"
        ));
        entityManager.flush();
        entityManager.clear();

        assertThat(hasActiveRestriction(unrestricted.getUserId(), now)).isFalse();
        assertThat(hasActiveRestriction(temporarilyRestricted.getUserId(), now)).isTrue();
        assertThat(hasActiveRestriction(permanentlyRestricted.getUserId(), now)).isTrue();
        assertThat(hasActiveRestriction(999_999L, now)).isFalse();
    }

    private boolean hasActiveRestriction(Long userId, LocalDateTime now) {
        return repository.existsActiveRestriction(
                userId,
                PenaltyType.PERMANENT_BAN,
                PenaltyType.SUSPENDED_7_DAYS,
                now
        );
    }

    private Users persistUser(String username) {
        return entityManager.persistAndFlush(Users.builder()
                .username(username)
                .passwordHash("encoded-password")
                .name(username)
                .email(username + "@example.com")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build());
    }

    private ReportCase persistCase(Users user, String targetKey) {
        return entityManager.persistAndFlush(ReportCase.open(
                targetKey,
                user,
                user.getUserId(),
                TargetType.USER
        ));
    }
}
