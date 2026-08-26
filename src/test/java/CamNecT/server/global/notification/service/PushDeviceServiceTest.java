package CamNecT.server.global.notification.service;

import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.notification.dto.request.RegisterPushTokenRequest;
import CamNecT.server.global.notification.model.Platform;
import CamNecT.server.global.notification.model.PushDevice;
import CamNecT.server.global.notification.model.PushTokenLockBucket;
import CamNecT.server.global.notification.repository.PushDeviceRepository;
import CamNecT.server.global.notification.repository.PushTokenLockBucketRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class PushDeviceServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PushDeviceRepository pushDeviceRepository = mock(PushDeviceRepository.class);
    private final PushTokenLockBucketRepository lockBucketRepository =
            mock(PushTokenLockBucketRepository.class);
    private final PushDeviceService pushDeviceService = new PushDeviceService(
            pushDeviceRepository,
            lockBucketRepository,
            userRepository
    );

    @Test
    void findEnabledTokensRemovesDuplicateTokens() {
        when(pushDeviceRepository.findAllByUserIdAndEnabledTrue(1L)).thenReturn(List.of(
                device("token-a"),
                device("token-a"),
                device("token-b")
        ));

        assertThat(pushDeviceService.findEnabledTokens(1L))
                .containsExactly("token-a", "token-b");
    }

    @Test
    void registerDisablesEveryPreviousOwnerOfTheSameToken() {
        RegisterPushTokenRequest request = new RegisterPushTokenRequest("device", Platform.WEB, "shared-token");
        allowTokenLocks();
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                Users.builder().userId(1L).status(UserStatus.ACTIVE).build()
        ));
        when(pushDeviceRepository.findByUserIdAndDeviceId(1L, "device")).thenReturn(Optional.empty());
        when(pushDeviceRepository.save(org.mockito.ArgumentMatchers.any(PushDevice.class)))
                .thenReturn(PushDevice.builder().id(10L).userId(1L).deviceId("device")
                        .platform(Platform.WEB).fcmToken("shared-token").enabled(true).build());

        pushDeviceService.register(1L, request);

        verify(pushDeviceRepository).disableActiveToken("shared-token");
    }

    @Test
    void registerNormalizesDeviceIdAndToken() {
        RegisterPushTokenRequest request = new RegisterPushTokenRequest(
                "  device  ",
                Platform.WEB,
                "  token  "
        );
        allowTokenLocks();
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                Users.builder().userId(1L).status(UserStatus.ACTIVE).build()
        ));
        when(pushDeviceRepository.findByUserIdAndDeviceId(1L, "device")).thenReturn(Optional.empty());
        when(pushDeviceRepository.save(any(PushDevice.class))).thenAnswer(invocation -> {
            PushDevice device = invocation.getArgument(0);
            return PushDevice.builder()
                    .id(10L)
                    .userId(device.getUserId())
                    .deviceId(device.getDeviceId())
                    .platform(device.getPlatform())
                    .fcmToken(device.getFcmToken())
                    .enabled(device.isEnabled())
                    .build();
        });

        pushDeviceService.register(1L, request);

        verify(pushDeviceRepository).disableActiveToken("token");
        verify(pushDeviceRepository).findByUserIdAndDeviceId(1L, "device");
        verify(pushDeviceRepository).save(org.mockito.ArgumentMatchers.argThat(device ->
                device.getDeviceId().equals("device") && device.getFcmToken().equals("token")
        ));
    }

    @Test
    void registerRejectsUserWithdrawnWhileWaitingForTheUserLock() {
        RegisterPushTokenRequest request = new RegisterPushTokenRequest(
                "device", Platform.WEB, "token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                Users.builder().userId(1L).status(UserStatus.WITHDRAWN).build()
        ));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> pushDeviceService.register(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_WITHDRAWN);
        verify(userRepository).lockUserRow(1L);
        verify(pushDeviceRepository, never()).disableActiveToken("token");
        verify(lockBucketRepository, never()).findByIdForUpdate(anyShort());
    }

    @Test
    void registerLocksOldAndNewTokenBucketsInStableOrderBeforeOwnershipTransfer() {
        RegisterPushTokenRequest request = new RegisterPushTokenRequest("device", Platform.WEB, "B");
        PushDevice existing = PushDevice.builder()
                .id(10L)
                .userId(1L)
                .deviceId("device")
                .platform(Platform.WEB)
                .fcmToken("A")
                .enabled(true)
                .build();
        allowTokenLocks();
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                Users.builder().userId(1L).status(UserStatus.ACTIVE).build()
        ));
        when(pushDeviceRepository.findTokenByUserIdAndDeviceId(1L, "device"))
                .thenReturn(Optional.of("A"));
        when(pushDeviceRepository.findByUserIdAndDeviceId(1L, "device"))
                .thenReturn(Optional.of(existing));
        when(pushDeviceRepository.save(existing)).thenReturn(existing);

        pushDeviceService.register(1L, request);

        InOrder order = inOrder(userRepository, pushDeviceRepository, lockBucketRepository);
        order.verify(userRepository).lockUserRow(1L);
        order.verify(pushDeviceRepository).findTokenByUserIdAndDeviceId(1L, "device");
        order.verify(lockBucketRepository).findByIdForUpdate((short) 1);
        order.verify(lockBucketRepository).findByIdForUpdate((short) 2);
        order.verify(pushDeviceRepository).disableActiveToken("B");
        order.verify(pushDeviceRepository).findByUserIdAndDeviceId(1L, "device");
        order.verify(pushDeviceRepository).save(existing);
    }

    @Test
    void disableAllForUserDisablesEveryActiveDeviceOwnedByUser() {
        allowTokenLocks();
        when(pushDeviceRepository.findActiveTokensByUserId(1L)).thenReturn(List.of("B", "A"));
        when(pushDeviceRepository.disableAllByUserId(1L)).thenReturn(2);

        int disabled = pushDeviceService.disableAllForUser(1L);

        assertThat(disabled).isEqualTo(2);
        InOrder order = inOrder(userRepository, pushDeviceRepository, lockBucketRepository);
        order.verify(userRepository).lockUserRow(1L);
        order.verify(pushDeviceRepository).findActiveTokensByUserId(1L);
        order.verify(lockBucketRepository).findByIdForUpdate((short) 1);
        order.verify(lockBucketRepository).findByIdForUpdate((short) 2);
        order.verify(pushDeviceRepository).disableAllByUserId(1L);
        verify(pushDeviceRepository).disableAllByUserId(1L);
    }

    @Test
    void disableForUserAndDeviceNormalizesAndDisablesOnlyThatDevice() {
        allowTokenLocks();
        when(pushDeviceRepository.findTokenByUserIdAndDeviceId(1L, "device-a"))
                .thenReturn(Optional.of("A"));
        when(pushDeviceRepository.disableByUserIdAndDeviceId(1L, "device-a")).thenReturn(1);

        int disabled = pushDeviceService.disableForUserAndDevice(1L, "  device-a  ");

        assertThat(disabled).isEqualTo(1);
        InOrder order = inOrder(userRepository, pushDeviceRepository, lockBucketRepository);
        order.verify(userRepository).lockUserRow(1L);
        order.verify(pushDeviceRepository).findTokenByUserIdAndDeviceId(1L, "device-a");
        order.verify(lockBucketRepository).findByIdForUpdate((short) 1);
        order.verify(pushDeviceRepository).disableByUserIdAndDeviceId(1L, "device-a");
    }

    @Test
    void invalidTokenCleanupUsesTheSameStableTokenLocks() {
        allowTokenLocks();

        pushDeviceService.disableTokens(List.of("B", "A", "B"));

        InOrder order = inOrder(lockBucketRepository, pushDeviceRepository);
        order.verify(lockBucketRepository).findByIdForUpdate((short) 1);
        order.verify(lockBucketRepository).findByIdForUpdate((short) 2);
        order.verify(pushDeviceRepository).disableActiveTokens(List.of("B", "A"));
    }

    private void allowTokenLocks() {
        when(lockBucketRepository.findByIdForUpdate(anyShort())).thenAnswer(invocation -> {
            short bucketId = invocation.getArgument(0);
            return Optional.of(PushTokenLockBucket.builder().bucketId(bucketId).build());
        });
    }

    private PushDevice device(String token) {
        return PushDevice.builder()
                .userId(1L)
                .deviceId("device-" + token)
                .platform(Platform.WEB)
                .fcmToken(token)
                .enabled(true)
                .build();
    }
}
