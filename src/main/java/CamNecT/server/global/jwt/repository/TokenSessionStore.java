package CamNecT.server.global.jwt.repository;

import java.time.Instant;

public interface TokenSessionStore {

    void save(
            Long userId,
            String sessionId,
            String accessTokenHash,
            Instant accessExpiresAt,
            String refreshTokenHash,
            Instant refreshExpiresAt
    );

    boolean containsAccessTokenHash(Long userId, String sessionId, String accessTokenHash);

    RefreshRotationResult rotate(
            Long userId,
            String sessionId,
            String currentRefreshTokenHash,
            String newAccessTokenHash,
            Instant newAccessExpiresAt,
            String newRefreshTokenHash,
            Instant newRefreshExpiresAt
    );

    void deleteSession(Long userId, String sessionId);

    void deleteAll(Long userId);

    enum RefreshRotationResult {
        ROTATED,
        NOT_FOUND,
        MISMATCH
    }
}
