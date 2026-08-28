package CamNecT.server.domain.profile.components.education.service;

import CamNecT.server.domain.profile.components.ProfileComponentAccessGuard;
import CamNecT.server.domain.profile.components.education.dto.request.EducationRequest;
import CamNecT.server.domain.profile.components.education.dto.response.EducationResponse;
import CamNecT.server.domain.profile.components.education.model.Education;
import CamNecT.server.domain.profile.components.education.model.EducationStatus;
import CamNecT.server.domain.profile.components.education.repository.EducationRepository;
import CamNecT.server.domain.profile.components.institutions.model.Campus;
import CamNecT.server.domain.profile.components.institutions.model.Institutions;
import CamNecT.server.domain.profile.components.institutions.repository.CampusRepository;
import CamNecT.server.domain.profile.components.institutions.repository.InstitutionRepository;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationServiceCampusTest {

    @Mock EducationRepository educationRepository;
    @Mock InstitutionRepository institutionRepository;
    @Mock CampusRepository campusRepository;
    @Mock ProfileComponentAccessGuard accessGuard;

    @InjectMocks EducationService service;

    @Test
    void addEducationStoresOnlyAnActiveCampusBelongingToTheInstitution() {
        Users user = Users.builder().userId(1L).build();
        Institutions institution = institution(20L, "건국대학교");
        Campus campus = campus(18L, institution, "서울캠퍼스");
        EducationRequest request = request(20L, 18L);

        when(accessGuard.requireAuthenticatedUserForUpdate(1L)).thenReturn(user);
        when(institutionRepository.findById(20L)).thenReturn(Optional.of(institution));
        when(campusRepository.findActiveByIdAndInstitutionId(18L, 20L))
                .thenReturn(Optional.of(campus));

        service.addEducation(1L, request);

        ArgumentCaptor<Education> captor = ArgumentCaptor.forClass(Education.class);
        verify(educationRepository).save(captor.capture());
        assertThat(captor.getValue().getInstitution()).isSameAs(institution);
        assertThat(captor.getValue().getCampus()).isSameAs(campus);
    }

    @Test
    void addEducationRejectsAMissingInactiveOrMismatchedCampus() {
        Users user = Users.builder().userId(1L).build();
        Institutions institution = institution(20L, "건국대학교");
        EducationRequest request = request(20L, 999L);

        when(accessGuard.requireAuthenticatedUserForUpdate(1L)).thenReturn(user);
        when(institutionRepository.findById(20L)).thenReturn(Optional.of(institution));
        when(campusRepository.findActiveByIdAndInstitutionId(999L, 20L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addEducation(1L, request))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.CAMPUS_NOT_FOUND));
        verify(educationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void legacyEducationWithoutCampusKeepsNullableResponseContract() {
        Education education = Education.builder()
                .educationId(10L)
                .institution(institution(20L, "건국대학교"))
                .startDate(LocalDate.of(2025, 3, 1))
                .status(EducationStatus.ATTENDING)
                .build();

        EducationResponse response = EducationResponse.from(education);

        assertThat(response.campusId()).isNull();
        assertThat(response.campusName()).isNull();
    }

    private EducationRequest request(Long institutionId, Long campusId) {
        return new EducationRequest(
                institutionId,
                campusId,
                LocalDate.of(2025, 3, 1),
                null,
                EducationStatus.ATTENDING,
                null
        );
    }

    private Institutions institution(Long id, String name) {
        return Institutions.builder()
                .institutionId(id)
                .institutionCode("INST-" + id)
                .institutionNameKor(name)
                .build();
    }

    private Campus campus(Long id, Institutions institution, String name) {
        return Campus.builder()
                .campusId(id)
                .institution(institution)
                .campusName(name)
                .fullCampusName(institution.getInstitutionNameKor() + " " + name)
                .campusRelation("본교")
                .campusOrder(1)
                .region("서울특별시")
                .build();
    }
}
