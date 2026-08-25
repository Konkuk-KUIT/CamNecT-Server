package CamNecT.server.global.common.auth;

import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.model.TokenType;
import CamNecT.server.global.jwt.util.JwtUtil;
import CamNecT.server.global.jwt.service.TokenSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final AccountAccessGuard accountAccessGuard = mock(AccountAccessGuard.class);
    private final TokenSessionService tokenSessionService = mock(TokenSessionService.class);
    private final AuthInterceptor interceptor = new AuthInterceptor(jwtUtil, accountAccessGuard, tokenSessionService);

    @Test
    void verificationTokenCarriesTheIssuingPasswordFingerprint() {
        JwtUtil actualJwtUtil = new JwtUtil(
                "test-jwt-secret-key-for-verification-token-lifecycle-tests",
                60_000L,
                120_000L,
                180_000L
        );

        String token = actualJwtUtil.generateVerificationToken(1L, UserRole.USER, "password-hash");

        assertThat(actualJwtUtil.getTokenType(token)).isEqualTo(TokenType.VERIFICATION);
        assertThat(actualJwtUtil.matchesPasswordFingerprint(
                actualJwtUtil.getPasswordFingerprint(token), "password-hash"
        )).isTrue();
    }

    @Test
    void allowsActiveAccountWithPendingInitialSetupToUseRegularApi() throws Exception {
        HttpServletRequest request = request("/api/community/posts");
        HttpServletResponse response = mock(HttpServletResponse.class);
        Users user = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        when(accountAccessGuard.requireAccessible(1L)).thenReturn(user);
        when(jwtUtil.getRole("token")).thenReturn(UserRole.USER);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(tokenSessionService).requireActiveAccess(1L, "token");
    }

    @Test
    void rejectsVerificationTokenFromProfileApi() {
        HttpServletRequest request = request("/api/profile/uploads/presign");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(jwtUtil.getTokenType("token")).thenReturn(TokenType.VERIFICATION);

        CustomException exception = assertThrows(CustomException.class,
                () -> interceptor.preHandle(request, response, new Object()));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.TOKEN_TYPE_NOT_ALLOWED);
    }

    @Test
    void allowsCurrentVerificationTokenOnlyWhileApprovalIsPending() throws Exception {
        HttpServletRequest request = verificationRequest();
        HttpServletResponse response = mock(HttpServletResponse.class);
        Users user = Users.builder()
                .userId(1L)
                .status(UserStatus.ADMIN_PENDING)
                .passwordHash("current-hash")
                .build();
        when(accountAccessGuard.requireAccessible(1L)).thenReturn(user);
        when(jwtUtil.getPasswordFingerprint("token")).thenReturn("fingerprint");
        when(jwtUtil.matchesPasswordFingerprint("fingerprint", "current-hash")).thenReturn(true);
        when(jwtUtil.getRole("token")).thenReturn(UserRole.USER);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(tokenSessionService, never()).requireActiveAccess(1L, "token");
    }

    @Test
    void rejectsVerificationTokenAfterPasswordChanges() {
        HttpServletRequest request = verificationRequest();
        Users user = Users.builder()
                .userId(1L)
                .status(UserStatus.ADMIN_PENDING)
                .passwordHash("changed-hash")
                .build();
        when(accountAccessGuard.requireAccessible(1L)).thenReturn(user);
        when(jwtUtil.getPasswordFingerprint("token")).thenReturn("old-fingerprint");

        CustomException exception = assertThrows(CustomException.class,
                () -> interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    void rejectsVerificationTokenAfterApproval() {
        HttpServletRequest request = verificationRequest();
        Users user = Users.builder()
                .userId(1L)
                .status(UserStatus.ACTIVE)
                .passwordHash("current-hash")
                .build();
        when(accountAccessGuard.requireAccessible(1L)).thenReturn(user);

        CustomException exception = assertThrows(CustomException.class,
                () -> interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
        verify(jwtUtil, never()).getPasswordFingerprint("token");
    }

    @Test
    void mapsLegacyVerificationTokenWithoutFingerprintToInvalidToken() {
        HttpServletRequest request = verificationRequest();
        Users user = Users.builder()
                .userId(1L)
                .status(UserStatus.ADMIN_PENDING)
                .passwordHash("current-hash")
                .build();
        when(accountAccessGuard.requireAccessible(1L)).thenReturn(user);
        when(jwtUtil.getPasswordFingerprint("token"))
                .thenThrow(new CustomException(ErrorCode.UNAUTHORIZED));

        CustomException exception = assertThrows(CustomException.class,
                () -> interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    private HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(request.getRequestURI()).thenReturn(uri);
        when(jwtUtil.getTokenType("token")).thenReturn(TokenType.ACCESS);
        when(jwtUtil.getUserId("token")).thenReturn(1L);
        return request;
    }

    private HttpServletRequest verificationRequest() {
        HttpServletRequest request = request("/api/verification/documents/me");
        when(jwtUtil.getTokenType("token")).thenReturn(TokenType.VERIFICATION);
        return request;
    }
}
