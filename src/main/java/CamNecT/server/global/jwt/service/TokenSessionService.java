package CamNecT.server.global.jwt.service;

import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.repository.TokenSessionStore;
import CamNecT.server.global.jwt.util.JwtUtil;
import CamNecT.server.global.jwt.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenSessionService {

    private final TokenSessionStore tokenSessionStore;
    private final JwtUtil jwtUtil;

    public void create(Long userId, String accessToken, String refreshToken) {
        tokenSessionStore.save(
                userId,
                TokenUtil.sha256Hex(accessToken),
                jwtUtil.getExpiration(accessToken),
                TokenUtil.sha256Hex(refreshToken),
                jwtUtil.getExpiration(refreshToken)
        );
    }

    public void requireActiveAccess(Long userId, String accessToken) {
        requireActiveAccessHash(userId, TokenUtil.sha256Hex(accessToken));
    }

    public void requireActiveAccessHash(Long userId, String accessTokenHash) {
        if (!tokenSessionStore.containsAccessTokenHash(userId, accessTokenHash)) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    public void rotate(
            Long userId,
            String currentRefreshToken,
            String newAccessToken,
            String newRefreshToken
    ) {
        TokenSessionStore.RefreshRotationResult result = tokenSessionStore.rotate(
                userId,
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

    public void revoke(Long userId) {
        tokenSessionStore.delete(userId);
    }

    public String accessTokenHash(String accessToken) {
        return TokenUtil.sha256Hex(accessToken);
    }

}
