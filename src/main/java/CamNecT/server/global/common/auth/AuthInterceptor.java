package CamNecT.server.global.common.auth;

import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.util.JwtUtil;
import CamNecT.server.global.jwt.model.TokenType;
import CamNecT.server.global.jwt.service.TokenSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final AccountAccessGuard accountAccessGuard;
    private final TokenSessionService tokenSessionService;

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {

        //제외할 부분은 WebMvcConfig에서 제외함
        // 1. OPTIONS 요청(Preflight)은 통과시킴 (CORS 문제 방지)
        if (request.getMethod().equals("OPTIONS")) {
            return true;
        }
        // 토큰 추출
        String token = extractBearer(request);
        //토큰 유효성 검사
        jwtUtil.validateOrThrow(token);

        TokenType type = jwtUtil.getTokenType(token);
        if (type == null) {
            throw new CustomException(AuthErrorCode.ACCESS_TOKEN_REQUIRED);
        }

        String uri = request.getRequestURI();

        if (type == TokenType.ACCESS) {
            // pass
        } else if (type == TokenType.VERIFICATION && isAllowedForVerificationToken(uri)) {
            // pass
        } else {
            throw new CustomException(AuthErrorCode.TOKEN_TYPE_NOT_ALLOWED);
        }

        Long userId = jwtUtil.getUserId(token);
        accountAccessGuard.requireAccessible(userId);
        if (type == TokenType.ACCESS) {
            String sessionId = tokenSessionService.requireActiveAccess(userId, token);
            request.setAttribute("sessionId", sessionId);
        }
        request.setAttribute("userId", userId);

        request.setAttribute("role", jwtUtil.getRole(token).name());

        return true;
    }

    //헤더로부터 Bearer 토큰 추출
    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
        return header.substring(7);
    }

    private boolean isAllowedForVerificationToken(String uri) {
        return uri.equals("/api/verification/documents")
                || uri.startsWith("/api/verification/documents/");
    }
}
