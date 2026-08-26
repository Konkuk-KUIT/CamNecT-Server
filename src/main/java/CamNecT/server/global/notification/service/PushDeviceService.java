package CamNecT.server.global.notification.service;

import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.notification.dto.request.RegisterPushTokenRequest;
import CamNecT.server.global.notification.model.PushDevice;
import CamNecT.server.global.notification.repository.PushDeviceRepository;
import CamNecT.server.global.notification.repository.PushTokenLockBucketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PushDeviceService {

    private static final int TOKEN_LOCK_BUCKET_COUNT = 64;

    private final PushDeviceRepository pushDeviceRepository;
    private final PushTokenLockBucketRepository pushTokenLockBucketRepository;
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

        String previousToken = pushDeviceRepository
                .findTokenByUserIdAndDeviceId(userId, deviceId)
                .orElse(null);

        // 사용자 행 다음 토큰 버킷을 오름차순으로 잠가 신규 토큰과 토큰 교환도 같은 순서로 직렬화한다.
        lockTokenBuckets(Stream.of(previousToken, token).toList());

        // 다른 사용자뿐 아니라 같은 사용자의 다른 deviceId도 비활성화해야 로그아웃 후 재전송되지 않는다.
        pushDeviceRepository.disableActiveToken(token);

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

        List<String> tokens = invalidTokens.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (tokens.isEmpty()) return;

        lockTokenBuckets(tokens);
        pushDeviceRepository.disableActiveTokens(tokens);
    }

    @Transactional
    public int disableAllForUser(Long userId) {
        userRepository.lockUserRow(userId);
        lockTokenBuckets(pushDeviceRepository.findActiveTokensByUserId(userId));
        return pushDeviceRepository.disableAllByUserId(userId);
    }

    @Transactional
    public int disableForUserAndDevice(Long userId, String deviceId) {
        String normalizedDeviceId = deviceId.trim();
        userRepository.lockUserRow(userId);
        pushDeviceRepository.findTokenByUserIdAndDeviceId(userId, normalizedDeviceId)
                .ifPresent(token -> lockTokenBuckets(List.of(token)));
        return pushDeviceRepository.disableByUserIdAndDeviceId(userId, normalizedDeviceId);
    }

    private void lockTokenBuckets(List<String> tokens) {
        tokens.stream()
                .filter(Objects::nonNull)
                .map(this::tokenLockBucket)
                .distinct()
                .sorted()
                .forEach(bucketId -> pushTokenLockBucketRepository.findByIdForUpdate(bucketId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Missing push token lock bucket: " + bucketId)));
    }

    private short tokenLockBucket(String token) {
        return (short) Math.floorMod(token.hashCode(), TOKEN_LOCK_BUCKET_COUNT);
    }

    public record RegisterResult(Long pushDeviceId, boolean created) {}
}
