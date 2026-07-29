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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@SuppressWarnings("unused") // /api/auth/refresh 재활성화 시 사용할 refresh token 회전 구현
public class AuthTokenService {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final TokenSessionService tokenSessionService;
    private final UserReportPenaltyService userReportPenaltyService;

    @Transactional
    public TokenRefreshResponse refreshAccessToken(String rawRefreshToken) {
        String refreshToken = normalize(rawRefreshToken);
        // 1) 서명/만료 검증
        jwtUtil.validateOrThrow(refreshToken);
        if (jwtUtil.getTokenType(refreshToken) != TokenType.REFRESH) {
            throw new CustomException(AuthErrorCode.TOKEN_TYPE_NOT_ALLOWED);
        }

        Long userId = jwtUtil.getUserId(refreshToken);
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == UserStatus.SUSPENDED
                || userReportPenaltyService.hasActiveRestriction(userId)) {
            tokenSessionService.revoke(userId);
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            tokenSessionService.revoke(userId);
            throw new CustomException(AuthErrorCode.USER_WITHDRAWN);
        }

        String newAccess = jwtUtil.generateAccessToken(userId, user.getRole());
        String newRefresh = jwtUtil.generateRefreshToken(userId, user.getRole());

        // 3) 저장값을 새 refresh로 교체(= 기존 refresh 즉시 무효화)
        tokenSessionService.rotate(userId, refreshToken, newAccess, newRefresh);

        return new TokenRefreshResponse(
                "Bearer",
                newAccess,
                jwtUtil.getAccessTokenExpirationMs(),
                newRefresh,
                jwtUtil.getRefreshTokenExpirationMs()
        );
    }

    private String normalize(String token) {
        if (token == null) return null;
        token = token.trim();
        if (token.startsWith("Bearer ")) return token.substring(7).trim();
        return token;
    }
}
