package CamNecT.server.domain.gifticon.repository;

import CamNecT.server.domain.gifticon.model.GifticonExportBatch;
import CamNecT.server.domain.gifticon.model.GifticonExportDeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GifticonExportBatchRepository extends JpaRepository<GifticonExportBatch, Long> {

    Optional<GifticonExportBatch> findTopByOrderByExportedAtDesc();

    @Query("""
            select b.id
            from GifticonExportBatch b
            where b.deliveryStatus = :status
              and (b.nextAttemptAt is null or b.nextAttemptAt <= :now)
            order by b.nextAttemptAt asc, b.id asc
            """)
    List<Long> findDueReadyBatchIds(
            @Param("status") GifticonExportDeliveryStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from GifticonExportBatch b where b.id = :batchId")
    Optional<GifticonExportBatch> findByIdForDelivery(@Param("batchId") Long batchId);
}
