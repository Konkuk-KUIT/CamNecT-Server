package CamNecT.server.global.notification.service;

import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.notification.dto.request.RegisterPushTokenRequest;
import CamNecT.server.global.notification.model.Platform;
import CamNecT.server.global.notification.model.PushDevice;
import CamNecT.server.global.notification.repository.PushDeviceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class PushDeviceServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PushDeviceRepository pushDeviceRepository = mock(PushDeviceRepository.class);
    private final PushDeviceService pushDeviceService = new PushDeviceService(pushDeviceRepository, userRepository);

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
    void registerDisablesSameTokenOwnedByAnotherUser() {
        RegisterPushTokenRequest request = new RegisterPushTokenRequest("device", Platform.WEB, "shared-token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                Users.builder().userId(1L).status(UserStatus.ACTIVE).build()
        ));
        when(pushDeviceRepository.findByUserIdAndDeviceId(1L, "device")).thenReturn(Optional.empty());
        when(pushDeviceRepository.save(org.mockito.ArgumentMatchers.any(PushDevice.class)))
                .thenReturn(PushDevice.builder().id(10L).userId(1L).deviceId("device")
                        .platform(Platform.WEB).fcmToken("shared-token").enabled(true).build());

        pushDeviceService.register(1L, request);

        verify(pushDeviceRepository).disableTokenForOtherUsers("shared-token", 1L);
    }

    @Test
    void registerNormalizesDeviceIdAndToken() {
        RegisterPushTokenRequest request = new RegisterPushTokenRequest(
                "  device  ",
                Platform.WEB,
                "  token  "
        );
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

        verify(pushDeviceRepository).disableTokenForOtherUsers("token", 1L);
        verify(pushDeviceRepository).findByUserIdAndDeviceId(1L, "device");
        verify(pushDeviceRepository).save(org.mockito.ArgumentMatchers.argThat(device ->
                device.getDeviceId().equals("device") && device.getFcmToken().equals("token")
        ));
    }

    @Test
    void disableAllForUserDisablesEveryActiveDeviceOwnedByUser() {
        when(pushDeviceRepository.disableAllByUserId(1L)).thenReturn(2);

        int disabled = pushDeviceService.disableAllForUser(1L);

        assertThat(disabled).isEqualTo(2);
        verify(pushDeviceRepository).disableAllByUserId(1L);
    }

    @Test
    void disableForUserAndDeviceNormalizesAndDisablesOnlyThatDevice() {
        when(pushDeviceRepository.disableByUserIdAndDeviceId(1L, "device-a")).thenReturn(1);

        int disabled = pushDeviceService.disableForUserAndDevice(1L, "  device-a  ");

        assertThat(disabled).isEqualTo(1);
        verify(pushDeviceRepository).disableByUserIdAndDeviceId(1L, "device-a");
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
