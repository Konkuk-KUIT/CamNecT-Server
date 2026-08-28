package CamNecT.server.global.notification.repository;

import CamNecT.server.global.notification.model.PushDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    Optional<PushDevice> findByUserIdAndDeviceId(Long userId, String deviceId);

    List<PushDevice> findAllByUserIdAndEnabledTrue(Long userId);

    @Query("select p.fcmToken from PushDevice p where p.userId = :userId and p.deviceId = :deviceId")
    Optional<String> findTokenByUserIdAndDeviceId(
            @Param("userId") Long userId,
            @Param("deviceId") String deviceId
    );

    @Query("select p.fcmToken from PushDevice p where p.userId = :userId and p.enabled = true")
    List<String> findActiveTokensByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PushDevice p
           set p.enabled = false,
               p.activeFcmToken = null,
               p.lastSeenAt = CURRENT_TIMESTAMP
         where p.fcmToken = :token
           and p.enabled = true
    """)
    int disableActiveToken(@Param("token") String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PushDevice p
           set p.enabled = false,
               p.activeFcmToken = null,
               p.lastSeenAt = CURRENT_TIMESTAMP
         where p.fcmToken in :tokens
           and p.enabled = true
    """)
    int disableActiveTokens(@Param("tokens") Collection<String> tokens);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PushDevice p
           set p.enabled = false,
               p.activeFcmToken = null,
               p.lastSeenAt = CURRENT_TIMESTAMP
         where p.userId = :userId
           and p.deviceId = :deviceId
           and p.enabled = true
    """)
    int disableByUserIdAndDeviceId(
            @Param("userId") Long userId,
            @Param("deviceId") String deviceId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PushDevice p
           set p.enabled = false,
               p.activeFcmToken = null,
               p.lastSeenAt = CURRENT_TIMESTAMP
         where p.userId = :userId
           and p.enabled = true
    """)
    int disableAllByUserId(@Param("userId") Long userId);
}
