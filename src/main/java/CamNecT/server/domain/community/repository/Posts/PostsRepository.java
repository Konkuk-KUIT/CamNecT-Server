package CamNecT.server.domain.community.repository.Posts;

import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.PostStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PostsRepository extends JpaRepository<Posts, Long> {

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @Query("select p from Posts p where p.id = :postId")
    Optional<Posts> findByIdForRead(@Param("postId") Long postId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Posts p where p.id = :postId")
    Optional<Posts> findByIdForUpdate(@Param("postId") Long postId);

    // LATEST(최신순): 기존 파생쿼리 + keyword/tagId까지 하려면 JPQL 하나 더 두는 게 편함
    @Query("""
        select p
        from Posts p
        where p.status = :status
          and (:code is null or p.board.code = :code)
          and (:cursorId is null or p.id < :cursorId)
          and (:keyword is null or p.title like concat('%', :keyword, '%') escape '!'
                           or p.content like concat('%', :keyword, '%') escape '!')
          and (:tagId is null or exists (
                select 1 from PostTags pt
                where pt.post = p and pt.tag.id = :tagId
          ))
        order by p.id desc
    """)
    Slice<Posts> findFeedLatestWithFilter(
            @Param("status") PostStatus status,
            @Param("code") BoardCode code,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // =========================
    // Waiting Questions (답변대기)  **루트댓글 기준으로 변경**
    // PostStats.rootCommentCount == 0
    // =========================
    @Query("""
        select p
        from Posts p
        join PostStats ps on ps.post = p
        where p.status = :status
          and p.board.code = :code
          and ps.rootCommentCount = 0
        order by p.id desc
    """)
    Slice<Posts> findWaitingQuestions(
            @Param("status") PostStatus status,
            @Param("code") BoardCode code,
            Pageable pageable
    );
    // =========================
    // ---- 아래부터는 PostQueryService용 확장 쿼리 ----
    // (tab/keyword/tag/cursor/sort)
    // =========================

    // RECOMMENDED(추천순 = hotScore)
    @Query("""
    select p
    from Posts p
    join PostStats ps on ps.post = p
    where p.status = :status
      and (:code is null or p.board.code = :code)
      and (:keyword is null or p.title like concat('%', :keyword, '%') escape '!'
                       or p.content like concat('%', :keyword, '%') escape '!')
      and (:tagId is null or exists (
            select 1 from PostTags pt
            where pt.post = p and pt.tag.id = :tagId
      ))
      and (
            :cursorValue is null
            or ps.hotScore < :cursorValue
            or (ps.hotScore = :cursorValue and p.id < :cursorId)
      )
    order by ps.hotScore desc, p.id desc
""")
    Slice<Posts> findFeedRecommended(
            @Param("status") PostStatus status,
            @Param("code") BoardCode code,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("cursorValue") Long cursorValue,   // hotScore 커서
            @Param("cursorId") Long cursorId,         // postId 커서
            Pageable pageable
    );

    // LIKE(좋아요순)
    @Query("""
    select p
    from Posts p
    join PostStats ps on ps.post = p
    where p.status = :status
      and (:code is null or p.board.code = :code)
      and (:keyword is null or p.title like concat('%', :keyword, '%') escape '!'
                       or p.content like concat('%', :keyword, '%') escape '!')
      and (:tagId is null or exists (
            select 1 from PostTags pt
            where pt.post = p and pt.tag.id = :tagId
      ))
      and (
            :cursorValue is null
            or ps.likeCount < :cursorValue
            or (ps.likeCount = :cursorValue and p.id < :cursorId)
      )
    order by ps.likeCount desc, p.id desc
""")
    Slice<Posts> findFeedLikeDesc(
            @Param("status") PostStatus status,
            @Param("code") BoardCode code,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("cursorValue") Long cursorValue,   // likeCount 커서
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
    select p
    from Posts p
    join PostStats ps on ps.post = p
    where p.status = :status
      and (:code is null or p.board.code = :code)
      and (:keyword is null or p.title like concat('%', :keyword, '%') escape '!'
                       or p.content like concat('%', :keyword, '%') escape '!')
      and (:tagId is null or exists (
            select 1 from PostTags pt
            where pt.post = p and pt.tag.id = :tagId
      ))
      and (
            :cursorValue is null
            or ps.bookmarkCount < :cursorValue
            or (ps.bookmarkCount = :cursorValue and p.id < :cursorId)
      )
    order by ps.bookmarkCount desc, p.id desc
""")
    Slice<Posts> findFeedBookmarkDesc(
            @Param("status") PostStatus status,
            @Param("code") BoardCode code,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("cursorValue") Long cursorValue,   // bookmarkCount 커서
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // 북마크 - 최신
    @Query("""
  select pb.post
  from PostBookmarks pb
  join pb.post p
  where pb.user.userId = :userId
    and p.status = :status
    and (:cursorId is null or p.id < :cursorId)
  order by p.id desc
""")
    Slice<Posts> findBookmarkedPostsLatest(
            @Param("userId") Long userId,
            @Param("status") PostStatus status,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // 북마크 - 추천(hotScore)
    @Query("""
  select p
  from PostBookmarks pb
  join pb.post p
  join PostStats ps on ps.post = p
  where pb.user.userId = :userId
    and p.status = :status
    and (
      :cursorValue is null
      or ps.hotScore < :cursorValue
      or (ps.hotScore = :cursorValue and p.id < :cursorId)
    )
  order by ps.hotScore desc, p.id desc
""")
    Slice<Posts> findBookmarkedPostsRecommended(
            @Param("userId") Long userId,
            @Param("status") PostStatus status,
            @Param("cursorValue") Long cursorValue,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
  select p
  from Posts p
  where p.user.userId = :userId
    and p.status = :status
    and (:cursorId is null or p.id < :cursorId)
  order by p.id desc
""")
    Slice<Posts> findMyPostsLatest(
            @Param("userId") Long userId,
            @Param("status") PostStatus status,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
  select p
  from Posts p
  join PostStats ps on ps.post = p
  where p.user.userId = :userId
    and p.status = :status
    and (
      :cursorValue is null
      or ps.hotScore < :cursorValue
      or (ps.hotScore = :cursorValue and p.id < :cursorId)
    )
  order by ps.hotScore desc, p.id desc
""")
    Slice<Posts> findMyPostsRecommended(
            @Param("userId") Long userId,
            @Param("status") PostStatus status,
            @Param("cursorValue") Long cursorValue,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE Posts p SET p.status = 'DELETED', p.deletedAt = :now WHERE p.id = :id")
    void softDeleteById(@Param("id") Long id, @Param("now") LocalDateTime now);
}
