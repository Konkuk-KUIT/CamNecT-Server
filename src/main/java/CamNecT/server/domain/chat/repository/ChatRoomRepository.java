package CamNecT.server.domain.chat.repository;

import CamNecT.server.domain.chat.model.ChatRequest;
import CamNecT.server.domain.chat.model.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 전체 조회
    @Query("SELECT r FROM ChatRoom r " +
            "JOIN FETCH r.request " +
            "JOIN FETCH r.requester " +
            "JOIN FETCH r.receiver " +
            "WHERE (r.requester.userId = :userId AND r.requesterExited = false) " +
            "OR (r.receiver.userId = :userId AND r.receiverExited = false) " +
            "ORDER BY r.lastMessageAt DESC")
    List<ChatRoom> findAllByUserIdWithBasicInfo(@Param("userId") Long userId);

    // 타입별 조회
    @Query("SELECT r FROM ChatRoom r " +
            "JOIN FETCH r.request req " +
            "JOIN FETCH r.requester " +
            "JOIN FETCH r.receiver " +
            "WHERE ((r.requester.userId = :userId AND r.requesterExited = false) " +
            "OR (r.receiver.userId = :userId AND r.receiverExited = false)) " +
            "AND req.type = :type " +
            "ORDER BY r.lastMessageAt DESC")
    List<ChatRoom> findAllByUserIdAndType(@Param("userId") Long userId,
                                          @Param("type") ChatRequest.RequestType type);

    // 상세 조회
    @Query("SELECT r FROM ChatRoom r " +
            "JOIN FETCH r.requester " +
            "JOIN FETCH r.receiver " +
            "JOIN FETCH r.request req " +
            "LEFT JOIN FETCH req.requestInterests " +
            "WHERE r.id = :roomId " +
            "AND ((r.requester.userId = :userId AND r.requesterExited = false) " +
            "OR (r.receiver.userId = :userId AND r.receiverExited = false))")
    Optional<ChatRoom> findByUserIdWithDetails(@Param("roomId") Long roomId, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select r from ChatRoom r join fetch r.requester join fetch r.receiver where r.id = :roomId")
    Optional<ChatRoom> findByIdForRead(@Param("roomId") Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ChatRoom r " +
            "JOIN FETCH r.requester " +
            "JOIN FETCH r.receiver " +
            "JOIN FETCH r.request " +
            "WHERE r.id = :roomId " +
            "AND ((r.requester.userId = :userId AND r.requesterExited = false) " +
            "OR (r.receiver.userId = :userId AND r.receiverExited = false))")
    Optional<ChatRoom> findByUserIdWithDetailsForUpdate(@Param("roomId") Long roomId, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r join fetch r.requester join fetch r.receiver where r.id = :roomId")
    Optional<ChatRoom> findByIdForUpdate(@Param("roomId") Long roomId);

    @Query("""
        select (count(r) > 0)
        from ChatRoom r
        where r.id = :roomId
          and ((r.requester.userId = :userId and r.requesterExited = false)
            or (r.receiver.userId = :userId and r.receiverExited = false))
    """)
    boolean existsAccessibleByUserId(@Param("roomId") Long roomId, @Param("userId") Long userId);

    @Query("""
        select case
            when r.requester.userId = :reporterId then r.receiver.userId
            when r.receiver.userId = :reporterId then r.requester.userId
            else null
        end
        from ChatRoom r
        where r.id = :roomId
    """)
    Optional<Long> findReportTargetUserId(
            @Param("roomId") Long roomId,
            @Param("reporterId") Long reporterId
    );

    @Query("""
        select distinct case
            when r.requester.userId = :userId then r.receiver.userId
            else r.requester.userId
        end
        from ChatRoom r
        join r.request cr
        where r.status = :roomStatus
          and cr.status = :requestStatus
          and cr.type = :requestType
          and ((r.requester.userId = :userId and r.receiver.userId in :targetIds)
            or (r.receiver.userId = :userId and r.requester.userId in :targetIds))
    """)
    List<Long> findActiveChatPartnerIds(
            @Param("userId") Long userId,
            @Param("targetIds") List<Long> targetIds,
            @Param("roomStatus") ChatRoom.RoomStatus roomStatus,
            @Param("requestStatus") ChatRequest.RequestStatus requestStatus,
            @Param("requestType") ChatRequest.RequestType requestType
    );

    Optional<ChatRoom> findByRequest_Id(Long requestId);
}
