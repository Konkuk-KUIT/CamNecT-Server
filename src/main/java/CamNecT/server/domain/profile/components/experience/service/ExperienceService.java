package CamNecT.server.domain.profile.components.experience.service;

import CamNecT.server.domain.profile.components.ProfileComponentAccessGuard;
import CamNecT.server.domain.profile.components.experience.dto.request.ExperienceRequest;
import CamNecT.server.domain.profile.components.experience.dto.response.ExperienceResponse;
import CamNecT.server.domain.profile.components.experience.model.Experience;
import CamNecT.server.domain.profile.components.experience.repository.ExperienceRepository;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExperienceService {

    private final ExperienceRepository experienceRepository;
    private final ProfileComponentAccessGuard accessGuard;

    @Transactional
    public void addExperience(Long userId, ExperienceRequest request) {
        Users user = accessGuard.requireAuthenticatedUserForUpdate(userId);

        Experience experience = Experience.builder()
                .user(user)
                .companyName(request.companyName())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .isCurrent(request.isCurrent())
                .responsibilities(request.responsibilities())
                .build();

        experienceRepository.save(experience);
    }

    public List<ExperienceResponse> getMyExperience(Long userId) {
        accessGuard.requireAuthenticatedUser(userId);
        return experienceRepository.findAllByUserIdWithDetails(userId)
                .stream()
                .map(ExperienceResponse::from)
                .toList();
    }

    @Transactional
    public void updateExperience(Long userId, Long experienceId, ExperienceRequest request) {
        accessGuard.requireAuthenticatedUserForUpdate(userId);
        Experience experience = experienceRepository.findByIdForUpdate(experienceId)
                .orElseThrow(() -> new CustomException(UserErrorCode.EXPERIENCE_NOT_FOUND));
        // 본인 확인
        if (!experience.getUser().getUserId().equals(userId)) {
            throw new CustomException(UserErrorCode.EXPERIENCE_FORBIDDEN);
        }

        experience.updateExperience(
                request.companyName(),
                request.startDate(),
                request.endDate(),
                request.isCurrent(),
                request.responsibilities()
        );
    }

    @Transactional
    public void deleteExperience(Long userId, Long experienceId) {
        accessGuard.requireAuthenticatedUserForUpdate(userId);
        Experience experience = experienceRepository.findByIdForUpdate(experienceId)
                .orElseThrow(() -> new CustomException(UserErrorCode.EXPERIENCE_NOT_FOUND));

        if (!experience.getUser().getUserId().equals(userId)) {
            throw new CustomException(UserErrorCode.EXPERIENCE_FORBIDDEN);
        }

        experienceRepository.delete(experience);
    }
}
