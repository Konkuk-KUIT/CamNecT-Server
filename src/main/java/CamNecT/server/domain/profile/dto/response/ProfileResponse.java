package CamNecT.server.domain.profile.dto.response;

import CamNecT.server.domain.profile.components.certificate.dto.response.CertificateResponse;
import CamNecT.server.domain.profile.components.education.dto.response.EducationResponse;
import CamNecT.server.domain.profile.components.experience.dto.response.ExperienceResponse;
import CamNecT.server.domain.portfolio.dto.response.PortfolioPreviewResponse;

import java.util.List;

public record ProfileResponse(
        Long userId,
        String name,
        ProfileBasicsDto basics,
        Boolean isFollowing,
        int following,
        int follower,
        Integer myPoint,
        Boolean hasChat,
        List<PortfolioPreviewResponse> portfolioProjectList,
        List<EducationResponse> educations,
        List<ExperienceResponse> experience,
        List<CertificateResponse> certificate,
        List<ProfileTagDto> tags
) {
    public record ProfileBasicsDto(
            String bio,
            Boolean openToCoffeeChat,
            Boolean isFollowerVisible,
            Boolean isEducationVisible,
            Boolean isExperienceVisible,
            Boolean isCertificateVisible,
            String profileImageUrl,
            String studentNo,
//            Integer yearLevel,
            Long institutionId,
            Long majorId
    ) {
    }
}


