package CamNecT.server.domain.activity.repository.recruitment;

import CamNecT.server.domain.activity.model.recruitment.RecruitmentBookmark;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecruitmentBookmarkRepository extends JpaRepository<RecruitmentBookmark, Long> {

    Optional<RecruitmentBookmark> findByUserIdAndRecruitId(Long userId, Long recruitId);

    boolean existsByUserIdAndRecruitId(Long userId, Long recruitId);

    void deleteByRecruitId(Long recruitId);

    // =========================
    // 북마크한 모집글 - 최신순
    // return: [0]=TeamRecruitment, [1]=bookmarkCnt
    // =========================
    @Query("""
      select r,
             (select count(b2) from RecruitmentBookmark b2 where b2.recruitId = r.recruitId) as bookmarkCnt
      from RecruitmentBookmark b
      join TeamRecruitment r on r.recruitId = b.recruitId
      where b.userId = :userId
        and (:cursorId is null or r.recruitId < :cursorId)
      order by r.recruitId desc
    """)
    Slice<Object[]> findBookmarkedRecruitmentsLatestWithCounts(
            @Param("userId") Long userId,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // =========================
    // 북마크한 모집글 - 추천순(북마크 수 desc)
    // cursorValue = bookmarkCnt 커서
    // return: [0]=TeamRecruitment, [1]=bookmarkCnt
    // =========================
    @Query("""
      select r,
             count(b2) as bookmarkCnt
      from RecruitmentBookmark b
      join TeamRecruitment r on r.recruitId = b.recruitId
      join RecruitmentBookmark b2 on b2.recruitId = r.recruitId
      where b.userId = :userId
      group by r
      having (
          :cursorValue is null
          or count(b2) < :cursorValue
          or (count(b2) = :cursorValue and r.recruitId < :cursorId)
      )
      order by count(b2) desc, r.recruitId desc
    """)
    Slice<Object[]> findBookmarkedRecruitmentsRecommendedWithCounts(
            @Param("userId") Long userId,
            @Param("cursorValue") Long cursorValue,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // =========================
    // 내가 작성한 모집글 - 최신순 (+북마크수)
    // return: [0]=TeamRecruitment, [1]=bookmarkCnt
    // =========================
    @Query("""
      select r,
             (select count(b2) from RecruitmentBookmark b2 where b2.recruitId = r.recruitId) as bookmarkCnt
      from TeamRecruitment r
      where r.userId = :userId
        and (:cursorId is null or r.recruitId < :cursorId)
      order by r.recruitId desc
    """)
    Slice<Object[]> findMyRecruitmentsLatestWithCounts(
            @Param("userId") Long userId,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // =========================
    // 내가 작성한 모집글 - 추천순(북마크 수 desc)
    // cursorValue = bookmarkCnt 커서
    // return: [0]=TeamRecruitment, [1]=bookmarkCnt
    // =========================
    @Query("""
    select r,
         count(b2) as bookmarkCnt
    from TeamRecruitment r
    left join RecruitmentBookmark b2 on b2.recruitId = r.recruitId
    where r.userId = :userId
    group by r
    having (
          :cursorValue is null
          or count(b2) < :cursorValue
          or (count(b2) = :cursorValue and r.recruitId < :cursorId)
    )
    order by count(b2) desc, r.recruitId desc
    """)
    Slice<Object[]> findMyRecruitmentsRecommendedWithCounts(
            @Param("userId") Long userId,
            @Param("cursorValue") Long cursorValue,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
