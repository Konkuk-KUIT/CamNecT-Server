package CamNecT.server.global.jwt.service;

import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.repository.InMemoryTokenSessionStore;
import CamNecT.server.global.jwt.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenSessionServiceTest {

    private JwtUtil jwtUtil;
    private TokenSessionService tokenSessionService;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(
                "test-jwt-secret-key-for-token-session-tests-at-least-32-characters",
                60_000L,
                120_000L,
                60_000L
        );
        tokenSessionService = new TokenSessionService(new InMemoryTokenSessionStore(), jwtUtil);
    }

    @Test
    void acceptsOnlyTheAccessTokenStoredForTheCurrentSession() {
        String access = jwtUtil.generateAccessToken(1L, UserRole.USER);
        String refresh = jwtUtil.generateRefreshToken(1L, UserRole.USER);
        tokenSessionService.create(1L, access, refresh);

        assertDoesNotThrow(() -> tokenSessionService.requireActiveAccess(1L, access));

        String anotherAccess = jwtUtil.generateAccessToken(1L, UserRole.USER);
        CustomException exception = assertThrows(
                CustomException.class,
                () -> tokenSessionService.requireActiveAccess(1L, anotherAccess)
        );
        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    void refreshRotationReplacesBothTokens() {
        String oldAccess = jwtUtil.generateAccessToken(1L, UserRole.USER);
        String oldRefresh = jwtUtil.generateRefreshToken(1L, UserRole.USER);
        tokenSessionService.create(1L, oldAccess, oldRefresh);

        String newAccess = jwtUtil.generateAccessToken(1L, UserRole.USER);
        String newRefresh = jwtUtil.generateRefreshToken(1L, UserRole.USER);
        tokenSessionService.rotate(1L, oldRefresh, newAccess, newRefresh);

        assertDoesNotThrow(() -> tokenSessionService.requireActiveAccess(1L, newAccess));
        assertDoesNotThrow(() -> tokenSessionService.requireActiveAccess(1L, oldAccess));
    }

    @Test
    void reusedRefreshTokenRevokesTheWholeSession() {
        String access = jwtUtil.generateAccessToken(1L, UserRole.USER);
        String refresh = jwtUtil.generateRefreshToken(1L, UserRole.USER);
        tokenSessionService.create(1L, access, refresh);

        String attackerRefresh = jwtUtil.generateRefreshToken(1L, UserRole.USER);
        String newAccess = jwtUtil.generateAccessToken(1L, UserRole.USER);
        String newRefresh = jwtUtil.generateRefreshToken(1L, UserRole.USER);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> tokenSessionService.rotate(1L, attackerRefresh, newAccess, newRefresh)
        );
        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSED);
        assertThrows(CustomException.class, () -> tokenSessionService.requireActiveAccess(1L, access));
    }

    @Test
    void logoutRevokesTheAccessToken() {
        String access = jwtUtil.generateAccessToken(1L, UserRole.USER);
        String refresh = jwtUtil.generateRefreshToken(1L, UserRole.USER);
        tokenSessionService.create(1L, access, refresh);

        tokenSessionService.revoke(1L);

        assertThrows(CustomException.class, () -> tokenSessionService.requireActiveAccess(1L, access));
    }
}
