package CamNecT.server.domain.users.repository;

import CamNecT.server.domain.users.model.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    // 내가 팔로우하는 사람 수 (팔로잉)
    int countByFollowerId(Long userId);

    // 나를 팔로우하는 사람 수 (팔로워)
    int countByFollowingId(Long userId);

    //팔로잉 여부 조회
    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    // 팔로우 취소 (언팔)
    void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);

    // 팔로워 목록조회
    List<UserFollow> findAllByFollowingId(Long userId);

    // 팔로잉 목록조회
    List<UserFollow> findAllByFollowerId(Long userId);

    @Query("SELECT f.followerId FROM UserFollow f WHERE f.followingId = :followingId")
    List<Long> findFollowerIdsByFollowingId(@Param("followingId") Long followingId);

    @Modifying
    @Query("DELETE FROM UserFollow f WHERE f.followerId = :userId OR f.followingId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
