package CamNecT.server.domain.report.repository;

import CamNecT.server.domain.report.model.ReportEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReportEvidenceRepository extends JpaRepository<ReportEvidence, Long> {

    List<ReportEvidence> findAllByReport_ReportIdInOrderByReport_ReportIdAscSortOrderAsc(
            Collection<Long> reportIds
    );

    Optional<ReportEvidence> findByEvidenceIdAndReport_ReportIdAndReport_ReportCase_CaseId(
            Long evidenceId,
            Long reportId,
            Long caseId
    );

}
