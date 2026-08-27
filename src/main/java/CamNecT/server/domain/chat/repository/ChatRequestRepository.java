package CamNecT.server.domain.chat.repository;

import CamNecT.server.domain.chat.model.ChatRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRequestRepository extends JpaRepository<ChatRequest, Long> {

    // 중복 신청 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select cr
        from ChatRequest cr
        join fetch cr.requester
        join fetch cr.receiver
        where cr.id = :requestId
    """)
    Optional<ChatRequest> findByIdForUpdate(@Param("requestId") Long requestId);

    boolean existsByRequester_UserIdAndReceiver_UserIdAndStatusAndType(
            Long requesterId,
            Long receiverId,
            ChatRequest.RequestStatus status,
            ChatRequest.RequestType type
    );

    boolean existsByRequester_UserIdAndReceiver_UserIdAndStatusAndTypeAndRecruitmentId(
            Long requesterId,
            Long receiverId,
            ChatRequest.RequestStatus status,
            ChatRequest.RequestType type,
            Long recruitmentId
    );

    long countByReceiver_UserIdAndTypeAndStatus(
            Long userId,
            ChatRequest.RequestType type,
            ChatRequest.RequestStatus status
    );

    @Query("""
                select cr
                from ChatRequest cr
                join fetch cr.requester r
                where cr.receiver.userId = :userId
                  and cr.type = :type
                  and cr.status = :status
                order by cr.createdAt desc
            """)
    List<ChatRequest> findLatestReceivedRequestsByType(
            @Param("userId") Long userId,
            @Param("type") ChatRequest.RequestType type,
            @Param("status") ChatRequest.RequestStatus status,
            Pageable pageable
    );

    @Query("SELECT cr FROM ChatRequest cr " +
            "JOIN FETCH cr.requester " +
            "WHERE cr.receiver.userId = :userId " +
            "AND cr.type = :type " +
            "AND cr.status = :status")
    List<ChatRequest> findAllByReceiver_UserIdAndTypeAndStatus(
            @Param("userId") Long userId,
            @Param("type") ChatRequest.RequestType type,
            @Param("status") ChatRequest.RequestStatus status
    );

    @Query("SELECT cr FROM ChatRequest cr " +
            "JOIN FETCH cr.requester " +
            "WHERE cr.receiver.userId = :userId " +
            "AND cr.type = :type " +
            "AND cr.recruitmentId = :recruitmentId " +
            "AND cr.status = :status")
    List<ChatRequest> findAllByReceiver_UserIdAndTypeAndRecruitmentIdAndStatus(
            @Param("userId") Long userId,
            @Param("type") ChatRequest.RequestType type,
            @Param("recruitmentId") Long recruitmentId,
            @Param("status") ChatRequest.RequestStatus status
    );

    @Query("SELECT cr FROM ChatRequest cr " +
            "JOIN FETCH cr.requester " +
            "WHERE cr.receiver.userId = :userId " +
            "AND cr.type = :type " +
            "AND cr.status = :status " +
            "ORDER BY cr.createdAt DESC")
    List<ChatRequest> findRequestsWithRequester(
            @Param("userId") Long userId,
            @Param("type") ChatRequest.RequestType type,
            @Param("status") ChatRequest.RequestStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select cr
            from ChatRequest cr
            where cr.type = :type
              and cr.recruitmentId = :recruitmentId
              and cr.status = :status
            order by cr.id asc
            """)
    List<ChatRequest> findAllByRecruitmentIdAndTypeAndStatusForUpdate(
            @Param("type") ChatRequest.RequestType type,
            @Param("recruitmentId") Long recruitmentId,
            @Param("status") ChatRequest.RequestStatus status
    );

    @Query("""
        select (count(cr) > 0)
        from ChatRequest cr
        where cr.receiver.userId = :userId
        and cr.status = :status
        and (:type is null or cr.type = :type)
        """)
    boolean existsReceivedPending(
            @Param("userId") Long userId,
            @Param("status") ChatRequest.RequestStatus status,
            @Param("type") ChatRequest.RequestType type
    );
}
