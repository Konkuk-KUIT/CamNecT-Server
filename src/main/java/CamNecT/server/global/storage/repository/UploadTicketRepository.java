package CamNecT.server.global.storage.repository;

import CamNecT.server.global.storage.model.UploadPurpose;
import CamNecT.server.global.storage.model.UploadTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadTicketRepository extends JpaRepository<UploadTicket, Long> {
    Optional<UploadTicket> findByStorageKey(String storageKey);
    List<UploadTicket> findAllByStorageKeyIn(Collection<String> storageKeys);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from UploadTicket t where t.storageKey = :storageKey")
    Optional<UploadTicket> findByStorageKeyForUpdate(@Param("storageKey") String storageKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
              from UploadTicket t
             where t.storageKey in :storageKeys
             order by t.storageKey asc
            """)
    List<UploadTicket> findAllByStorageKeyInForUpdate(
            @Param("storageKeys") Collection<String> storageKeys
    );


    @Modifying
    @Query(value = """
        UPDATE upload_tickets
        SET status = 'EXPIRED'
        WHERE status = 'PENDING'
          AND expires_at < :now
        """, nativeQuery = true)
    void bulkExpirePending(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    update UploadTicket t
       set t.status = CamNecT.server.global.storage.model.UploadTicket.Status.EXPIRED
     where t.userId = :userId
       and t.purpose = :purpose
       and t.status = CamNecT.server.global.storage.model.UploadTicket.Status.PENDING
       and t.expiresAt <= :now
""")
    void bulkExpirePendingByUserPurpose(
            @Param("userId") Long userId,
            @Param("purpose") UploadPurpose purpose,
            @Param("now") LocalDateTime now
    );

    long countByUserIdAndPurposeAndStatusAndExpiresAtAfter(
            Long userId, UploadPurpose purpose, UploadTicket.Status status, LocalDateTime now
    );

}
