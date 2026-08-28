package CamNecT.server.domain.profile.components.education.dto.response;

import CamNecT.server.domain.profile.components.education.model.Education;
import CamNecT.server.domain.profile.components.education.model.EducationStatus;

import java.time.LocalDate;

public record EducationResponse(
        Long educationId,
        String schoolName,
        Long campusId,
        String campusName,
//        String majorName,
//        String degree,
        LocalDate startDate,
        LocalDate endDate,
        EducationStatus status,
        String description
) {
    public static EducationResponse from(Education education) {
        return new EducationResponse(
                education.getEducationId(),
                education.getInstitution().getInstitutionNameKor(),
                education.getCampus() == null ? null : education.getCampus().getCampusId(),
                education.getCampus() == null ? null : education.getCampus().getCampusName(),
//                education.getMajor().getMajorNameKor(),
//                education.getDegree(),
                education.getStartDate(),
                education.getEndDate(),
                education.getStatus(),
                education.getDescription()
        );
    }
}
