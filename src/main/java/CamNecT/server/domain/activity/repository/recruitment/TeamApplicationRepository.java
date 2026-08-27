package CamNecT.server.domain.activity.repository.recruitment;

import CamNecT.server.domain.activity.model.recruitment.TeamApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamApplicationRepository extends JpaRepository<TeamApplication, Long> {

    boolean existsByRecruitIdAndUserId(Long recruitId, Long userId);

    void deleteByRecruitId(Long recruitId);
}
