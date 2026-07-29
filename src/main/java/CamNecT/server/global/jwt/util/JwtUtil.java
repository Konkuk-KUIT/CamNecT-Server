package CamNecT.server.global.jwt.util;

import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import CamNecT.server.global.jwt.model.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
@Getter
public class JwtUtil {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;
    private final long verificationTokenExpirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs,
            @Value("${jwt.verification-token-expiration-ms}") long verificationTokenExpirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.verificationTokenExpirationMs = verificationTokenExpirationMs;
    }

    public String generateAccessToken(Long userId, UserRole role) {
        return generateToken(userId, role, TokenType.ACCESS, accessTokenExpirationMs);
    }

    public String generateRefreshToken(Long userId, UserRole role) {
        return generateToken(userId, role, TokenType.REFRESH, refreshTokenExpirationMs);
    }

    public String generateVerificationToken(Long userId, UserRole role) {
        return generateToken(userId, role, TokenType.VERIFICATION, verificationTokenExpirationMs);
    }

    public String generatePasswordResetToken(Long userId, UserRole role) {
        return generateToken(userId, role, TokenType.PASSWORD_RESET, verificationTokenExpirationMs);
    }

    private String generateToken(Long userId, UserRole role, TokenType type, long expirationMs) {
        if (userId == null) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR, new IllegalArgumentException("userId is null"));
        }
        if (role == null) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR, new IllegalArgumentException("role is null"));
        }

        Instant now = Instant.now();
        Instant exp = now.plusMillis(expirationMs);

        return Jwts.builder()
                .header().type("JWT").and()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type.name())
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public Long getUserId(String token) {
        String sub = parseClaims(token).getSubject();
        try {
            return Long.valueOf(sub);
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, new IllegalArgumentException("토큰 subject가 userId가 아닙니다: " + sub, e));
        }
    }

    public UserRole getRole(String token) {
        Object raw = parseClaims(token).get(CLAIM_ROLE);
        if (raw == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, new IllegalArgumentException("토큰 role claim이 없습니다."));
        }
        try {
            return UserRole.valueOf(raw.toString());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, new IllegalArgumentException("토큰 role claim이 올바르지 않습니다: " + raw, e));
        }
    }

    public TokenType getTokenType(String token) {
        Object raw = parseClaims(token).get(CLAIM_TYPE);
        if (raw == null) return null;
        try {
            return TokenType.valueOf(raw.toString());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, new IllegalArgumentException("토큰 type claim이 올바르지 않습니다: " + raw, e));
        }
    }

    public Instant getExpiration(String token) {
        Date expiration = parseClaims(token).getExpiration();
        return expiration.toInstant();
    }

    public void validateOrThrow(String token) {
        parseClaims(token);
    }

    private Claims parseClaims(String token) {
        if (!StringUtils.hasText(token)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, new IllegalArgumentException("token이 비어있습니다."));
        }
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, e);
        }
    }
}
