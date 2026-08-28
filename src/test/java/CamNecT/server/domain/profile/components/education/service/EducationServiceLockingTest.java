package CamNecT.server.domain.profile.components.education.service;

import CamNecT.server.domain.profile.components.ProfileComponentAccessGuard;
import CamNecT.server.domain.profile.components.education.dto.request.EducationRequest;
import CamNecT.server.domain.profile.components.education.model.Education;
import CamNecT.server.domain.profile.components.education.model.EducationStatus;
import CamNecT.server.domain.profile.components.education.repository.EducationRepository;
import CamNecT.server.domain.profile.components.institutions.model.Campus;
import CamNecT.server.domain.profile.components.institutions.model.Institutions;
import CamNecT.server.domain.profile.components.institutions.repository.CampusRepository;
import CamNecT.server.domain.profile.components.institutions.repository.InstitutionRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationServiceLockingTest {

    @Mock EducationRepository educationRepository;
    @Mock InstitutionRepository institutionRepository;
    @Mock CampusRepository campusRepository;
    @Mock ProfileComponentAccessGuard accessGuard;

    @InjectMocks EducationService service;

    @Test
    void everyMutationUsesTheSameUserLockAsWithdrawal() {
        Users owner = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        Institutions institution = org.mockito.Mockito.mock(Institutions.class);
        Campus campus = org.mockito.Mockito.mock(Campus.class);
        Education education = Education.builder()
                .educationId(10L)
                .user(owner)
                .institution(institution)
                .startDate(LocalDate.of(2025, 1, 1))
                .status(EducationStatus.ATTENDING)
                .build();
        EducationRequest request = new EducationRequest(
                20L, 30L, LocalDate.of(2025, 1, 1), null,
                EducationStatus.ATTENDING, null);
        when(accessGuard.requireAuthenticatedUserForUpdate(1L)).thenReturn(owner);
        when(institutionRepository.findById(20L)).thenReturn(Optional.of(institution));
        when(institution.getInstitutionId()).thenReturn(20L);
        when(campusRepository.findActiveByIdAndInstitutionId(30L, 20L))
                .thenReturn(Optional.of(campus));
        when(educationRepository.findById(10L)).thenReturn(Optional.of(education));

        service.addEducation(1L, request);
        service.updateEducation(1L, 10L, request);
        service.deleteEducation(1L, 10L);

        verify(accessGuard, times(3)).requireAuthenticatedUserForUpdate(1L);
    }
}
