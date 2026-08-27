package CamNecT.server.domain.profile.components.education.repository;

import CamNecT.server.domain.profile.components.education.model.Education;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EducationRepository extends JpaRepository<Education, Long> {

    @Query("SELECT e FROM Education e " +
            "LEFT JOIN FETCH e.institution " +
            "LEFT JOIN FETCH e.campus " +
            "WHERE e.user.userId = :userId " +
            "ORDER BY e.startDate DESC")
    List<Education> findAllByUserIdWithDetails(@Param("userId") Long userId);

    void deleteByUser_UserId(Long userId);
}