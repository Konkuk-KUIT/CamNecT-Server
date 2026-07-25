package CamNecT.server.domain.community.repository.Posts;

import CamNecT.server.domain.community.model.Posts.PostStats;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostStatsRepository extends JpaRepository<PostStats, Long> {

    Optional<PostStats> findByPost_Id(Long postId);

    List<PostStats> findByPost_IdIn(Collection<Long> postIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PostStats ps where ps.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ps from PostStats ps where ps.post.id = :postId")
    Optional<PostStats> findByPostIdForUpdate(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true)
    @Query("""
        update PostStats ps
           set ps.viewCount = ps.viewCount + 1,
               ps.hotScore = ps.likeCount * 3 + ps.commentCount * 5 + ps.viewCount + 1,
               ps.lastActivityAt = :now
         where ps.post.id = :postId
    """)
    int incrementView(@Param("postId") Long postId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("update PostStats ps set ps.lastActivityAt = :now where ps.post.id = :postId")
    int touchByPostId(@Param("postId") Long postId, @Param("now") LocalDateTime now);
}
