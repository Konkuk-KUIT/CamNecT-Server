package CamNecT.server.global.notification.repository;

import CamNecT.server.global.notification.model.PushTokenLockBucket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PushTokenLockBucketRepository extends JpaRepository<PushTokenLockBucket, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PushTokenLockBucket b where b.bucketId = :bucketId")
    Optional<PushTokenLockBucket> findByIdForUpdate(@Param("bucketId") Short bucketId);
}
