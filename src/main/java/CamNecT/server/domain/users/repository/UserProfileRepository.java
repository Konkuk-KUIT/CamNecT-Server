package CamNecT.server.domain.users.repository;

import CamNecT.server.domain.profile.dto.ProfileGlobalDto;
import CamNecT.server.domain.users.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    //userId에 해당하는 프로필 조회
    Optional<UserProfile> findByUserId(Long userId);

    //List<Long> 형식으로 들어오는 유저 id에 해당하는 프로필들 반환
    List<UserProfile> findAllByUserIdIn(Collection<Long> userIds);

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndOpenToCoffeeChatTrue(Long userId);

    @Query("""
                select up
                from UserProfile up
                join fetch up.user u
                where up.userId in :userIds
            """)
    List<UserProfile> findAllByUserIdInWithUser(@Param("userIds") List<Long> userIds);

    @Query("""
            select new CamNecT.server.domain.profile.dto.ProfileGlobalDto(
                p.userId,
                u.name,
                m.majorNameKor,
                p.studentNo,
                p.profileImageKey
            )
            from UserProfile p
            join p.user u
            left join p.major m
            where p.userId in :userIds
            """)
    List<ProfileGlobalDto> findGlobalsByUserIdIn(@Param("userIds") List<Long> userIds);

    @Query("""
            select new CamNecT.server.domain.profile.dto.ProfileGlobalDto(
                p.userId,
                u.name,
                m.majorNameKor,
                p.studentNo,
                p.profileImageKey
            )
            from UserProfile p
            join p.user u
            left join p.major m
            where p.userId = :userId
            """)
    Optional<ProfileGlobalDto> findGlobalByUserId(@Param("userId") Long userId);

    @Query("select p.profileImageKey from UserProfile p where p.userId = :userId")
    Optional<String> findProfileImageKeyByUserId(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
}
