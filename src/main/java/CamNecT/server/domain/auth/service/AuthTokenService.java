package CamNecT.server.domain.auth.service;

import CamNecT.server.domain.auth.dto.others.TokenRefreshResponse;
import CamNecT.server.domain.report.service.UserReportPenaltyService;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.service.TokenSessionService;
import CamNecT.server.global.jwt.util.JwtUtil;
import CamNecT.server.global.jwt.model.TokenType;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthTokenService {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final TokenSessionService tokenSessionService;
    private final UserReportPenaltyService userReportPenaltyService;

    @Transactional
    public TokenRefreshResponse refreshAccessToken(String rawRefreshToken) {
        String refreshToken = normalize(rawRefreshToken);
        validateRefreshToken(refreshToken);
        if (getTokenType(refreshToken) != TokenType.REFRESH) {
            throw new CustomException(AuthErrorCode.TOKEN_TYPE_NOT_ALLOWED);
        }

        Long userId = getUserId(refreshToken);
        String sessionId = getSessionId(refreshToken);
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == UserStatus.SUSPENDED
                || userReportPenaltyService.hasActiveRestriction(userId)) {
            tokenSessionService.revokeAll(userId);
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            tokenSessionService.revokeAll(userId);
            throw new CustomException(AuthErrorCode.USER_WITHDRAWN);
        }

        String newAccess = jwtUtil.generateAccessToken(userId, user.getRole(), sessionId);
        String newRefresh = jwtUtil.generateRefreshToken(userId, user.getRole(), sessionId);

        // Redis에서 저장 토큰 대조와 새 access/refresh 저장을 원자적으로 수행한다.
        tokenSessionService.rotate(userId, refreshToken, newAccess, newRefresh);

        return new TokenRefreshResponse(
                "Bearer",
                newAccess,
                jwtUtil.getAccessTokenExpirationMs(),
                newRefresh,
                jwtUtil.getRefreshTokenExpirationMs()
        );
    }

    private void validateRefreshToken(String refreshToken) {
        try {
            jwtUtil.validateOrThrow(refreshToken);
        } catch (CustomException e) {
            if (hasCause(e, ExpiredJwtException.class)) {
                throw new CustomException(AuthErrorCode.REFRESH_TOKEN_EXPIRED, e);
            }
            throw new CustomException(AuthErrorCode.INVALID_TOKEN, e);
        }
    }

    private TokenType getTokenType(String refreshToken) {
        try {
            return jwtUtil.getTokenType(refreshToken);
        } catch (CustomException e) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN, e);
        }
    }

    private Long getUserId(String refreshToken) {
        try {
            return jwtUtil.getUserId(refreshToken);
        } catch (CustomException e) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN, e);
        }
    }

    private String getSessionId(String refreshToken) {
        try {
            return jwtUtil.getSessionId(refreshToken);
        } catch (CustomException e) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN, e);
        }
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String normalize(String token) {
        if (token == null) return null;
        token = token.trim();
        if (token.startsWith("Bearer ")) return token.substring(7).trim();
        return token;
    }
}
