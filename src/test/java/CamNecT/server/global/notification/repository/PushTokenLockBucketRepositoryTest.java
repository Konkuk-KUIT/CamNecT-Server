package CamNecT.server.global.notification.repository;

import CamNecT.server.global.common.config.QuerydslConfig;
import CamNecT.server.global.notification.model.PushTokenLockBucket;
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
class PushTokenLockBucketRepositoryTest {

    @Autowired
    private PushTokenLockBucketRepository repository;

    @Test
    void readsTheSharedBucketWithAPessimisticWriteLock() throws NoSuchMethodException {
        repository.saveAndFlush(PushTokenLockBucket.builder().bucketId((short) 7).build());

        assertThat(repository.findByIdForUpdate((short) 7).orElseThrow().getBucketId())
                .isEqualTo((short) 7);
        assertThat(PushTokenLockBucketRepository.class
                .getMethod("findByIdForUpdate", Short.class)
                .getAnnotation(Lock.class)
                .value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
