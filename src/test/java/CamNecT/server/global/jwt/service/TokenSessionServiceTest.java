package CamNecT.server.global.jwt.service;

import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.repository.InMemoryTokenSessionStore;
import CamNecT.server.global.jwt.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.time.Clock;
import java.util.UUID;

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
        tokenSessionService = new TokenSessionService(new InMemoryTokenSessionStore(Clock.systemUTC()), jwtUtil);
    }

    @Test
    void acceptsOnlyTheAccessTokenStoredForItsSession() {
        String sessionId = sessionId();
        Tokens tokens = tokens(1L, sessionId);
        tokenSessionService.create(1L, tokens.access(), tokens.refresh());

        assertDoesNotThrow(() -> tokenSessionService.requireActiveAccess(1L, tokens.access()));

        String anotherAccess = jwtUtil.generateAccessToken(1L, UserRole.USER, sessionId);
        CustomException exception = assertThrows(
                CustomException.class,
                () -> tokenSessionService.requireActiveAccess(1L, anotherAccess)
        );
        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    void refreshRotationReplacesTokensOnlyInsideTheSameSession() {
        String sessionA = sessionId();
        String sessionB = sessionId();
        Tokens oldA = tokens(1L, sessionA);
        Tokens tokensB = tokens(1L, sessionB);
        tokenSessionService.create(1L, oldA.access(), oldA.refresh());
        tokenSessionService.create(1L, tokensB.access(), tokensB.refresh());

        Tokens newA = tokens(1L, sessionA);
        tokenSessionService.rotate(1L, oldA.refresh(), newA.access(), newA.refresh());

        assertDoesNotThrow(() -> tokenSessionService.requireActiveAccess(1L, newA.access()));
        assertDoesNotThrow(() -> tokenSessionService.requireActiveAccess(1L, oldA.access()));
        assertDoesNotThrow(() -> tokenSessionService.requireActiveAccess(1L, tokensB.access()));
    }

    @Test
    void reusedRefreshTokenRevokesOnlyItsSession() {
        String sessionA = sessionId();
        String sessionB = sessionId();
        Tokens tokensA = tokens(1L, sessionA);
        Tokens tokensB = tokens(1L, sessionB);
        tokenSessionService.create(1L, tokensA.access(), tokensA.refresh());
        tokenSessionService.create(1L, tokensB.access(), tokensB.refresh());

        String unregisteredRefreshA = jwtUtil.generateRefreshToken(1L, UserRole.USER, sessionA);
        Tokens nextA = tokens(1L, sessionA);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> tokenSessionService.rotate(1L, unregisteredRefreshA, nextA.access(), nextA.refresh())
        );
        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSED);
        assertThrows(CustomException.class, () -> tokenSessionService.requireActiveAccess(1L, tokensA.access()));
        assertDoesNotThrow(() -> tokenSessionService.requireActiveAccess(1L, tokensB.access()));
    }

    @Test
    void currentSessionLogoutKeepsOtherSessionsActive() {
        String sessionA = sessionId();
        String sessionB = sessionId();
        Tokens tokensA = tokens(1L, sessionA);
        Tokens tokensB = tokens(1L, sessionB);
        tokenSessionService.create(1L, tokensA.access(), tokensA.refresh());
        tokenSessionService.create(1L, tokensB.access(), tokensB.refresh());

        tokenSessionService.revokeSession(1L, sessionA);

        assertThrows(CustomException.class, () -> tokenSessionService.requireActiveAccess(1L, tokensA.access()));
        assertDoesNotThrow(() -> tokenSessionService.requireActiveAccess(1L, tokensB.access()));
    }

    @Test
    void securityEventRevokesEverySession() {
        Tokens tokensA = tokens(1L, sessionId());
        Tokens tokensB = tokens(1L, sessionId());
        tokenSessionService.create(1L, tokensA.access(), tokensA.refresh());
        tokenSessionService.create(1L, tokensB.access(), tokensB.refresh());

        tokenSessionService.revokeAll(1L);

        assertThrows(CustomException.class, () -> tokenSessionService.requireActiveAccess(1L, tokensA.access()));
        assertThrows(CustomException.class, () -> tokenSessionService.requireActiveAccess(1L, tokensB.access()));
    }

    @Test
    void inMemoryRevokeAllSerializesWithRefreshRotation() throws NoSuchMethodException {
        int modifiers = InMemoryTokenSessionStore.class
                .getMethod("deleteAll", Long.class)
                .getModifiers();

        assertThat(Modifier.isSynchronized(modifiers)).isTrue();
    }

    private Tokens tokens(Long userId, String sessionId) {
        return new Tokens(
                jwtUtil.generateAccessToken(userId, UserRole.USER, sessionId),
                jwtUtil.generateRefreshToken(userId, UserRole.USER, sessionId)
        );
    }

    private String sessionId() {
        return UUID.randomUUID().toString();
    }

    private record Tokens(String access, String refresh) {}
}
