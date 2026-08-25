package CamNecT.server.global.notification.service;

import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.notification.dto.request.RegisterPushTokenRequest;
import CamNecT.server.global.notification.model.PushDevice;
import CamNecT.server.global.notification.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PushDeviceService {

    private final PushDeviceRepository pushDeviceRepository;
    private final UserRepository userRepository;

    @Transactional
    public RegisterResult register(Long userId, RegisterPushTokenRequest req) {
        String deviceId = req.deviceId().trim();
        String token = req.token().trim();

        userRepository.lockUserRow(userId);
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(AuthErrorCode.USER_WITHDRAWN);
        }

        // 동일 FCM 토큰이 이전 로그인 사용자의 활성 디바이스로 남아 잘못 전송되는 것을 방지한다.
        pushDeviceRepository.disableTokenForOtherUsers(token, userId);

        PushDevice device = pushDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .map(existing -> {
                    existing.updateToken(req.platform(), token);
                    return existing;
                })
                .orElseGet(() -> PushDevice.builder()
                        .userId(userId)
                        .deviceId(deviceId)
                        .platform(req.platform())
                        .fcmToken(token)
                        .enabled(true)
                        .build());

        boolean created = (device.getId() == null);
        PushDevice saved = pushDeviceRepository.save(device);
        return new RegisterResult(saved.getId(), created);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<String> findEnabledTokens(Long userId) {
        return pushDeviceRepository.findAllByUserIdAndEnabledTrue(userId)
                .stream()
                .map(PushDevice::getFcmToken)
                .distinct()
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void disableTokens(List<String> invalidTokens) {
        if (invalidTokens == null || invalidTokens.isEmpty()) return;

        List<PushDevice> devices = pushDeviceRepository.findAllByFcmTokenIn(invalidTokens);
        for (PushDevice d : devices) d.disable();
        pushDeviceRepository.saveAll(devices);
    }

    @Transactional
    public int disableAllForUser(Long userId) {
        return pushDeviceRepository.disableAllByUserId(userId);
    }

    @Transactional
    public int disableForUserAndDevice(Long userId, String deviceId) {
        return pushDeviceRepository.disableByUserIdAndDeviceId(userId, deviceId.trim());
    }

    public record RegisterResult(Long pushDeviceId, boolean created) {}
}
