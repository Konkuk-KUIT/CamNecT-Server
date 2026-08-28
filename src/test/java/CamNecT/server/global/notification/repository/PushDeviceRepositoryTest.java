package CamNecT.server.global.notification.repository;

import CamNecT.server.global.common.config.QuerydslConfig;
import CamNecT.server.global.notification.model.Platform;
import CamNecT.server.global.notification.model.PushDevice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class PushDeviceRepositoryTest {

    @Autowired
    private PushDeviceRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void databaseRejectsTwoActiveOwnersOfTheSameToken() {
        repository.saveAndFlush(device(1L, "device-a", "shared-token", true));

        assertThatThrownBy(() -> repository.saveAndFlush(
                device(2L, "device-b", "shared-token", true)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ownershipTransferClearsTheGuardBeforeEnablingTheNewDevice() {
        PushDevice previous = repository.saveAndFlush(
                device(1L, "device-a", "shared-token", true)
        );

        assertThat(repository.disableActiveToken("shared-token")).isEqualTo(1);
        entityManager.clear();

        PushDevice disabled = repository.findById(previous.getId()).orElseThrow();
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.getActiveFcmToken()).isNull();

        PushDevice current = repository.saveAndFlush(
                device(1L, "device-b", "shared-token", true)
        );
        assertThat(current.isEnabled()).isTrue();
        assertThat(current.getActiveFcmToken()).isEqualTo("shared-token");
    }

    @Test
    void disabledHistoryRowsMayKeepTheSameToken() {
        repository.saveAndFlush(device(1L, "device-a", "shared-token", false));
        repository.saveAndFlush(device(2L, "device-b", "shared-token", false));

        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findAll())
                .extracting(PushDevice::getActiveFcmToken)
                .containsOnlyNulls();
    }

    private PushDevice device(Long userId, String deviceId, String token, boolean enabled) {
        return PushDevice.builder()
                .userId(userId)
                .deviceId(deviceId)
                .platform(Platform.WEB)
                .fcmToken(token)
                .enabled(enabled)
                .build();
    }
}
