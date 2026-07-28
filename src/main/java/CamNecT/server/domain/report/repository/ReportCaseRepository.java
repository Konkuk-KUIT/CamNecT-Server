package CamNecT.server.domain.report.repository;

import CamNecT.server.domain.report.model.ReportCase;
import CamNecT.server.domain.report.model.ReportStatus;
import CamNecT.server.domain.report.model.TargetType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReportCaseRepository extends JpaRepository<ReportCase, Long> {

    Optional<ReportCase> findByTargetKey(String targetKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReportCase c where c.caseId = :caseId")
    Optional<ReportCase> findByIdForUpdate(@Param("caseId") Long caseId);

    @Query("""
            select c
            from ReportCase c
            where (:type is null or c.targetType = :type)
              and (:status is null or c.status = :status)
            """)
    Page<ReportCase> findAllByFilters(
            @Param("type") TargetType type,
            @Param("status") ReportStatus status,
            Pageable pageable
    );
}
