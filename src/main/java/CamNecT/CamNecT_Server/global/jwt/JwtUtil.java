package CamNecT.CamNecT_Server.global.jwt;

import CamNecT.CamNecT_Server.global.jwt.model.TokenType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Component
@Getter
public class JwtUtil {
    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs
    ){
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(String username) {
        return generateToken(username, TokenType.ACCESS, accessTokenExpirationMs);
    }

    public String generateRefreshToken(String username) {
        return generateToken(username, TokenType.REFRESH, refreshTokenExpirationMs);
    }

    private String generateToken(String username, TokenType type, long expirationMs) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationMs);

        return Jwts.builder()
                .header().type("JWT").and()
                .subject(username)
                .claim("type", type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public String getUsername(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload().getSubject();
    }

    public TokenType getTokenType(String token) {
        Object type = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload()
                .get("type");


        return type == null ? null : TokenType.valueOf(type.toString());
    }

    public Instant getExpiration(String token) {
        Date expiration = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();

        return expiration.toInstant();
    }

    public boolean validate(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
