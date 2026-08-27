package CamNecT.server.domain.report.repository;

import CamNecT.server.domain.report.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterIdAndReportCase_CaseId(Long reporterId, Long caseId);

    List<Report> findAllByReportCase_CaseIdOrderByCreatedAtAsc(Long caseId);

    Optional<Report> findByReportIdAndReportCase_CaseId(Long reportId, Long caseId);
}
