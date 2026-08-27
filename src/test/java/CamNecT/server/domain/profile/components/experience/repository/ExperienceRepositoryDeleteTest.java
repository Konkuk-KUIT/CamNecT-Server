package CamNecT.server.domain.profile.components.experience.repository;

import CamNecT.server.domain.profile.components.experience.model.Experience;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class ExperienceRepositoryDeleteTest {

    @Autowired TestEntityManager entityManager;
    @Autowired ExperienceRepository experienceRepository;

    @Test
    void userBulkDeletionRemovesResponsibilitiesBeforeExperienceParents() {
        Users user = entityManager.persist(Users.builder()
                .username("experience-delete-user")
                .passwordHash("hash")
                .name("경력 사용자")
                .email("experience-delete@example.com")
                .build());
        entityManager.persist(Experience.builder()
                .user(user)
                .companyName("회사")
                .startDate(LocalDate.of(2024, 1, 1))
                .isCurrent(true)
                .responsibilities(new ArrayList<>(List.of("업무 1", "업무 2")))
                .build());
        entityManager.flush();
        entityManager.clear();

        experienceRepository.deleteByUser_UserId(user.getUserId());
        entityManager.flush();

        Number responsibilityCount = (Number) entityManager.getEntityManager()
                .createNativeQuery("select count(*) from experience_responsibilities")
                .getSingleResult();
        assertThat(experienceRepository.findAll()).isEmpty();
        assertThat(responsibilityCount.longValue()).isZero();
    }
}
