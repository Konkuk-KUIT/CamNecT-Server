package CamNecT.server.global.jwt.service;

import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.repository.TokenSessionStore;
import CamNecT.server.global.jwt.util.JwtUtil;
import CamNecT.server.global.jwt.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TokenSessionService {

    private final TokenSessionStore tokenSessionStore;
    private final JwtUtil jwtUtil;

    public void create(Long userId, String accessToken, String refreshToken) {
        String sessionId = requireSessionId(accessToken);
        String refreshSessionId = requireSessionId(refreshToken);
        if (!Objects.equals(sessionId, refreshSessionId)) {
            throw new CustomException(
                    ErrorCode.INTERNAL_ERROR,
                    new IllegalArgumentException("Access/Refresh Token sessionId가 일치하지 않습니다.")
            );
        }
        tokenSessionStore.save(
                userId,
                sessionId,
                TokenUtil.sha256Hex(accessToken),
                jwtUtil.getExpiration(accessToken),
                TokenUtil.sha256Hex(refreshToken),
                jwtUtil.getExpiration(refreshToken)
        );
    }

    public String requireActiveAccess(Long userId, String accessToken) {
        String sessionId = requireSessionId(accessToken);
        requireActiveAccessHash(userId, sessionId, TokenUtil.sha256Hex(accessToken));
        return sessionId;
    }

    public void requireActiveAccessHash(Long userId, String sessionId, String accessTokenHash) {
        if (!tokenSessionStore.containsAccessTokenHash(userId, sessionId, accessTokenHash)) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    public void rotate(
            Long userId,
            String currentRefreshToken,
            String newAccessToken,
            String newRefreshToken
    ) {
        String sessionId = requireSessionId(currentRefreshToken);
        if (!Objects.equals(sessionId, requireSessionId(newAccessToken))
                || !Objects.equals(sessionId, requireSessionId(newRefreshToken))) {
            throw new CustomException(
                    ErrorCode.INTERNAL_ERROR,
                    new IllegalArgumentException("Rotation Token sessionId가 일치하지 않습니다.")
            );
        }
        TokenSessionStore.RefreshRotationResult result = tokenSessionStore.rotate(
                userId,
                sessionId,
                TokenUtil.sha256Hex(currentRefreshToken),
                TokenUtil.sha256Hex(newAccessToken),
                jwtUtil.getExpiration(newAccessToken),
                TokenUtil.sha256Hex(newRefreshToken),
                jwtUtil.getExpiration(newRefreshToken)
        );

        if (result == TokenSessionStore.RefreshRotationResult.MISMATCH) {
            throw new CustomException(AuthErrorCode.REFRESH_TOKEN_REUSED);
        }
        if (result == TokenSessionStore.RefreshRotationResult.NOT_FOUND) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    public void revokeSession(Long userId, String sessionId) {
        tokenSessionStore.deleteSession(userId, sessionId);
    }

    public void revokeAll(Long userId) {
        tokenSessionStore.deleteAll(userId);
    }

    public String accessTokenHash(String accessToken) {
        return TokenUtil.sha256Hex(accessToken);
    }

    private String requireSessionId(String token) {
        try {
            return jwtUtil.getSessionId(token);
        } catch (CustomException e) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN, e);
        }
    }

}
