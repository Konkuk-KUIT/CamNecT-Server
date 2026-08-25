package CamNecT.server.domain.profile.components.experience.service;

import CamNecT.server.domain.profile.components.ProfileComponentAccessGuard;
import CamNecT.server.domain.profile.components.experience.dto.request.ExperienceRequest;
import CamNecT.server.domain.profile.components.experience.model.Experience;
import CamNecT.server.domain.profile.components.experience.repository.ExperienceRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperienceServiceLockingTest {

    @Mock ExperienceRepository experienceRepository;
    @Mock ProfileComponentAccessGuard accessGuard;

    @InjectMocks ExperienceService service;

    @Test
    void updateLocksUserBeforeLoadingExperienceWithResponsibilities() {
        Users owner = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        Experience experience = experience(owner);
        ExperienceRequest request = request(List.of("변경 업무"));
        when(accessGuard.requireAuthenticatedUserForUpdate(1L)).thenReturn(owner);
        when(experienceRepository.findById(10L)).thenReturn(Optional.of(experience));

        service.updateExperience(1L, 10L, request);

        InOrder order = inOrder(accessGuard, experienceRepository);
        order.verify(accessGuard).requireAuthenticatedUserForUpdate(1L);
        order.verify(experienceRepository).findById(10L);
        assertThat(experience.getResponsibilities()).containsExactly("변경 업무");
    }

    @Test
    void deleteLocksUserBeforeLoadingExperienceParent() {
        Users owner = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        Experience experience = experience(owner);
        when(accessGuard.requireAuthenticatedUserForUpdate(1L)).thenReturn(owner);
        when(experienceRepository.findById(10L)).thenReturn(Optional.of(experience));

        service.deleteExperience(1L, 10L);

        InOrder order = inOrder(accessGuard, experienceRepository);
        order.verify(accessGuard).requireAuthenticatedUserForUpdate(1L);
        order.verify(experienceRepository).findById(10L);
        order.verify(experienceRepository).delete(experience);
    }

    @Test
    void creationUsesTheSameUserLockAsWithdrawal() {
        Users owner = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        when(accessGuard.requireAuthenticatedUserForUpdate(1L)).thenReturn(owner);

        service.addExperience(1L, request(List.of("업무")));

        verify(accessGuard).requireAuthenticatedUserForUpdate(1L);
        verify(experienceRepository).save(org.mockito.ArgumentMatchers.any(Experience.class));
    }

    private Experience experience(Users owner) {
        return Experience.builder()
                .experienceId(10L)
                .user(owner)
                .companyName("회사")
                .startDate(LocalDate.of(2024, 1, 1))
                .isCurrent(true)
                .responsibilities(new ArrayList<>(List.of("기존 업무")))
                .build();
    }

    private ExperienceRequest request(List<String> responsibilities) {
        return new ExperienceRequest(
                "회사",
                LocalDate.of(2024, 1, 1),
                null,
                true,
                responsibilities
        );
    }
}
