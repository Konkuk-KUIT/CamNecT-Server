package CamNecT.server.domain.verification.email.repository;

import CamNecT.server.domain.verification.email.model.EmailVerificationLockBucket;
import CamNecT.server.global.common.config.QuerydslConfig;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class EmailVerificationLockBucketRepositoryTest {

    @Autowired
    private EmailVerificationLockBucketRepository repository;

    @Test
    void readsSharedBucketWithPessimisticWriteLock() throws NoSuchMethodException {
        repository.saveAndFlush(EmailVerificationLockBucket.builder().bucketId((short) 7).build());

        assertThat(repository.findByIdForUpdate((short) 7).orElseThrow().getBucketId())
                .isEqualTo((short) 7);
        assertThat(EmailVerificationLockBucketRepository.class
                .getMethod("findByIdForUpdate", Short.class)
                .getAnnotation(Lock.class)
                .value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
