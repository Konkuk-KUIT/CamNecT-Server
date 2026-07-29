package CamNecT.server.global.common.auth;

import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.exception.CustomException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final AccountAccessGuard accountAccessGuard = mock(AccountAccessGuard.class);
    private final TokenSessionService tokenSessionService = mock(TokenSessionService.class);
    private final AuthInterceptor interceptor = new AuthInterceptor(jwtUtil, accountAccessGuard, tokenSessionService);

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

    private HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(request.getRequestURI()).thenReturn(uri);
        when(jwtUtil.getTokenType("token")).thenReturn(TokenType.ACCESS);
        when(jwtUtil.getUserId("token")).thenReturn(1L);
        return request;
    }
}
