package CamNecT.server.domain.profile.components.education.service;

import CamNecT.server.domain.profile.components.ProfileComponentAccessGuard;
import CamNecT.server.domain.profile.components.education.dto.request.EducationRequest;
import CamNecT.server.domain.profile.components.education.dto.response.EducationResponse;
import CamNecT.server.domain.profile.components.education.model.Education;
import CamNecT.server.domain.profile.components.education.repository.EducationRepository;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import CamNecT.server.domain.profile.components.institutions.repository.InstitutionRepository;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.domain.profile.components.institutions.model.Institutions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EducationService {

    private final EducationRepository educationRepository;
    private final InstitutionRepository institutionRepository;
    private final ProfileComponentAccessGuard accessGuard;

    @Transactional
    public void addEducation(Long userId, EducationRequest request) {
        Users user = accessGuard.requireAuthenticatedUserForUpdate(userId);

        Institutions institution = institutionRepository.findById(request.institutionId())
                .orElseThrow(() -> new CustomException(UserErrorCode.INSTITUTION_NOT_FOUND));


        Education education = Education.builder()
                .user(user)
                .institution(institution)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(request.status())
                .description(request.description())
                .build();

        educationRepository.save(education);
    }

    public List<EducationResponse> getMyEducations(Long userId) {
        accessGuard.requireAuthenticatedUser(userId);
        return educationRepository.findAllByUserIdWithDetails(userId)
                .stream()
                .map(EducationResponse::from)
                .toList();
    }

    @Transactional
    public void updateEducation(Long userId, Long educationId, EducationRequest request) {
        accessGuard.requireAuthenticatedUserForUpdate(userId);
        Education education = educationRepository.findById(educationId)
                .orElseThrow(() -> new CustomException(UserErrorCode.EDUCATION_NOT_FOUND));

        // 본인 확인
        if (!education.getUser().getUserId().equals(userId)) {
            throw new CustomException(UserErrorCode.EDUCATION_FORBIDDEN);
        }

        Institutions institution = institutionRepository.findById(request.institutionId())
                .orElseThrow(() -> new CustomException(UserErrorCode.INSTITUTION_NOT_FOUND));


        education.updateEducation(
                institution,
                request.startDate(),
                request.endDate(),
                request.status(),
                request.description()
        );
    }

    @Transactional
    public void deleteEducation(Long userId, Long educationId) {
        accessGuard.requireAuthenticatedUserForUpdate(userId);
        Education education = educationRepository.findById(educationId)
                .orElseThrow(() -> new CustomException(UserErrorCode.EDUCATION_NOT_FOUND));

        if (!education.getUser().getUserId().equals(userId)) {
            throw new CustomException(UserErrorCode.EDUCATION_FORBIDDEN);
        }
        educationRepository.delete(education);
    }
}
