package CamNecT.server.domain.auth.service;

import CamNecT.server.domain.profile.dto.request.UpdatePasswordRequest;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.service.TokenSessionService;
import CamNecT.server.global.jwt.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenSessionService tokenSessionService;
    private final JwtUtil jwtUtil;

    @Transactional
    public void updateMyPassword(Long userId, UpdatePasswordRequest req) {
        Users user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        validateRecoverableUser(user);

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new CustomException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        validatePassword(req.newPassword());
        user.changePasswordHash(passwordEncoder.encode(req.newPassword()));
        tokenSessionService.revokeAll(userId);
    }

    @Transactional
    public void resetPasswordByUserId(
            Long userId,
            String newPassword,
            String expectedPasswordFingerprint
    ) {
        Users user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.USER_NOT_FOUND));
        validateRecoverableUser(user);
        if (!jwtUtil.matchesPasswordFingerprint(expectedPasswordFingerprint, user.getPasswordHash())) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }

        resetPassword(user, newPassword);
        tokenSessionService.revokeAll(userId);
    }

    private void resetPassword(Users user, String newPassword) {
        validatePassword(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new CustomException(AuthErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        user.changePasswordHash(passwordEncoder.encode(newPassword));
    }

    private void validateRecoverableUser(Users user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(AuthErrorCode.USER_WITHDRAWN);
        }
    }

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.{8,16}$)(?=.*[a-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+\\[\\]{}\\\\|;:'\",.<>/?`~=-]+$"
    );

    protected void validatePassword(String pw) {
        if (pw == null || !PASSWORD_PATTERN.matcher(pw).matches()) {
            throw new CustomException(AuthErrorCode.INVALID_PASSWORD);
        }
    }
}
