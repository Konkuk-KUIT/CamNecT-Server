package CamNecT.server.domain.profile.components.institutions.repository;

import CamNecT.server.domain.profile.components.institutions.model.Campus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampusRepository extends JpaRepository<Campus, Long> {

    List<Campus> findByInstitution_InstitutionIdAndIsActiveTrueOrderByCampusOrderAsc(Long institutionId);
}
