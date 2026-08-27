package CamNecT.server.domain.profile.components.experience.repository;

import CamNecT.server.domain.profile.components.experience.model.Experience;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExperienceRepository extends JpaRepository<Experience, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Experience e where e.experienceId = :experienceId")
    Optional<Experience> findByIdForUpdate(@Param("experienceId") Long experienceId);

    @Query("SELECT DISTINCT e FROM Experience e " +
            "LEFT JOIN FETCH e.responsibilities " +
            "WHERE e.user.userId = :userId " +
            "ORDER BY e.startDate DESC")
    List<Experience> findAllByUserIdWithDetails(@Param("userId") Long userId);

    void deleteByUser_UserId(Long userId);
}
