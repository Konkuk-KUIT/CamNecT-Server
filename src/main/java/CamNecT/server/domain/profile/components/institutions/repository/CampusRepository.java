package CamNecT.server.domain.profile.components.institutions.repository;

import CamNecT.server.domain.profile.components.institutions.model.Campus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CampusRepository extends JpaRepository<Campus, Long> {

    List<Campus> findByInstitution_InstitutionIdAndIsActiveTrueOrderByCampusOrderAsc(Long institutionId);

    @Query("""
            SELECT c
            FROM Campus c
            WHERE c.campusId = :campusId
              AND c.institution.institutionId = :institutionId
              AND c.isActive = true
            """)
    Optional<Campus> findActiveByIdAndInstitutionId(
            @Param("campusId") Long campusId,
            @Param("institutionId") Long institutionId
    );

    @Query("""
            SELECT c
            FROM Campus c
            WHERE c.institution.institutionId IN :institutionIds
              AND c.isActive = true
            ORDER BY c.institution.institutionId ASC, c.campusOrder ASC
            """)
    List<Campus> findActiveByInstitutionIds(@Param("institutionIds") List<Long> institutionIds);
}
