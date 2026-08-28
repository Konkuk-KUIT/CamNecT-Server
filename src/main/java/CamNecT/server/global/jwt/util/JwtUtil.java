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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
@Getter
public class JwtUtil {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_SESSION_ID = "sid";
    private static final String CLAIM_PASSWORD_FINGERPRINT = "pwd";

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

    public String generateAccessToken(Long userId, UserRole role, String sessionId) {
        return generateSessionToken(userId, role, TokenType.ACCESS, accessTokenExpirationMs, sessionId);
    }

    public String generateRefreshToken(Long userId, UserRole role, String sessionId) {
        return generateSessionToken(userId, role, TokenType.REFRESH, refreshTokenExpirationMs, sessionId);
    }

    public String generateVerificationToken(Long userId, UserRole role, String passwordHash) {
        return tokenBuilder(userId, role, TokenType.VERIFICATION, verificationTokenExpirationMs)
                .claim(CLAIM_PASSWORD_FINGERPRINT, passwordFingerprint(passwordHash))
                .compact();
    }

    public String generatePasswordResetToken(Long userId, UserRole role, String passwordHash) {
        return tokenBuilder(userId, role, TokenType.PASSWORD_RESET, verificationTokenExpirationMs)
                .claim(CLAIM_PASSWORD_FINGERPRINT, passwordFingerprint(passwordHash))
                .compact();
    }

    private String generateSessionToken(
            Long userId,
            UserRole role,
            TokenType type,
            long expirationMs,
            String sessionId
    ) {
        requireValidSessionId(sessionId);
        return tokenBuilder(userId, role, type, expirationMs)
                .claim(CLAIM_SESSION_ID, sessionId)
                .compact();
    }

    private io.jsonwebtoken.JwtBuilder tokenBuilder(
            Long userId,
            UserRole role,
            TokenType type,
            long expirationMs
    ) {
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
                .signWith(key);
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

    public String getSessionId(String token) {
        Object raw = parseClaims(token).get(CLAIM_SESSION_ID);
        if (raw == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, new IllegalArgumentException("토큰 sid claim이 없습니다."));
        }
        String sessionId = raw.toString();
        try {
            return UUID.fromString(sessionId).toString();
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, new IllegalArgumentException("토큰 sid claim이 올바르지 않습니다.", e));
        }
    }

    public String getPasswordFingerprint(String token) {
        Object raw = parseClaims(token).get(CLAIM_PASSWORD_FINGERPRINT);
        if (raw == null || !StringUtils.hasText(raw.toString())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED,
                    new IllegalArgumentException("토큰 password fingerprint claim이 없습니다."));
        }
        return raw.toString();
    }

    public boolean matchesPasswordFingerprint(String expectedFingerprint, String passwordHash) {
        if (!StringUtils.hasText(expectedFingerprint)) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedFingerprint.getBytes(StandardCharsets.UTF_8),
                passwordFingerprint(passwordHash).getBytes(StandardCharsets.UTF_8)
        );
    }

    public Instant getExpiration(String token) {
        Date expiration = parseClaims(token).getExpiration();
        return expiration.toInstant();
    }

    public void validateOrThrow(String token) {
        parseClaims(token);
    }

    private void requireValidSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR, new IllegalArgumentException("sessionId is blank"));
        }
        try {
            UUID.fromString(sessionId);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR, new IllegalArgumentException("sessionId is not a UUID", e));
        }
    }

    private String passwordFingerprint(String passwordHash) {
        if (!StringUtils.hasText(passwordHash)) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR,
                    new IllegalArgumentException("passwordHash is blank"));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR, e);
        }
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
