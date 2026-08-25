package CamNecT.server.domain.profile.components.experience.repository;

import CamNecT.server.domain.profile.components.experience.model.Experience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperienceRepository extends JpaRepository<Experience, Long> {
    @Query("SELECT DISTINCT e FROM Experience e " +
            "LEFT JOIN FETCH e.responsibilities " +
            "WHERE e.user.userId = :userId " +
            "ORDER BY e.startDate DESC")
    List<Experience> findAllByUserIdWithDetails(@Param("userId") Long userId);

    void deleteByUser_UserId(Long userId);
}
