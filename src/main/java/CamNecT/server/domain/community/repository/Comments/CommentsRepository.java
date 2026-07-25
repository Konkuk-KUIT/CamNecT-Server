package CamNecT.server.domain.community.repository.Comments;

import CamNecT.server.domain.community.model.Comments.Comments;
import CamNecT.server.domain.community.model.enums.CommentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommentsRepository extends JpaRepository<Comments, Long> {

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Comments c where c.id = :commentId")
    Optional<Comments> findByIdForUpdate(@Param("commentId") Long commentId);

    // 루트 댓글(부모=null) 조회 (최신순/오래된순은 취향)
    List<Comments> findByPost_IdAndParentIsNullAndStatusOrderByCreatedAtDesc(Long postId, CommentStatus status, Pageable pageable);

    // 루트 댓글 여러 개에 대한 답글을 한 번에 조회(부모 아래 created_at 정렬)
    List<Comments> findByPost_IdAndParent_IdInAndStatusOrderByParent_IdAscCreatedAtAsc(Long postId, Collection<Long> parentIds, CommentStatus status);

    // 게시글 삭제 시: 댓글 하드 삭제
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Comments c where c.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);

}
