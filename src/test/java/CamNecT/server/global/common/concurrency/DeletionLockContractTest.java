package CamNecT.server.global.common.concurrency;

import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityRepository;
import CamNecT.server.domain.community.repository.Comments.CommentsRepository;
import CamNecT.server.domain.profile.components.experience.repository.ExperienceRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DeletionLockContractTest {

    @Test
    void deletionRaceBarriersUsePessimisticWriteLocks() throws NoSuchMethodException {
        assertWriteLock(CommentsRepository.class, "findAllByPostIdForUpdate", Long.class);
        assertWriteLock(ExternalActivityRepository.class, "findByIdForUpdate", Long.class);
        assertWriteLock(ExperienceRepository.class, "findByIdForUpdate", Long.class);
        assertWriteLock(UserRepository.class, "findByIdForUpdate", Long.class);
    }

    private void assertWriteLock(Class<?> repository, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = repository.getMethod(methodName, parameterTypes);
        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock)
                .as("%s.%s must declare @Lock", repository.getSimpleName(), methodName)
                .isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
