package CamNecT.server.domain.verification.email.repository;

import CamNecT.server.domain.verification.email.model.EmailVerificationLockBucket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailVerificationLockBucketRepository
        extends JpaRepository<EmailVerificationLockBucket, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from EmailVerificationLockBucket b where b.bucketId = :bucketId")
    Optional<EmailVerificationLockBucket> findByIdForUpdate(@Param("bucketId") Short bucketId);
}
