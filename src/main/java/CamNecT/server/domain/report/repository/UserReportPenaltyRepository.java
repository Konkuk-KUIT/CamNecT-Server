package CamNecT.server.domain.report.repository;

import CamNecT.server.domain.report.model.PenaltyType;
import CamNecT.server.domain.report.model.UserReportPenalty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface UserReportPenaltyRepository extends JpaRepository<UserReportPenalty, Long> {

    boolean existsByReportCase_CaseId(Long caseId);

    long countByUser_UserId(Long userId);

    boolean existsByUser_UserIdAndPenaltyType(Long userId, PenaltyType penaltyType);

    boolean existsByUser_UserIdAndPenaltyTypeAndSuspensionEndDateAfter(
            Long userId,
            PenaltyType penaltyType,
            LocalDateTime now
    );

    List<UserReportPenalty> findAllByUser_UserIdOrderByCreatedAtDesc(Long userId);
}
