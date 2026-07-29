package CamNecT.server.global.jwt.repository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.auth.token-store", havingValue = "in-memory")
public class InMemoryTokenSessionStore implements TokenSessionStore {

    private final ConcurrentHashMap<Long, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(
            Long userId,
            String accessTokenHash,
            Instant accessExpiresAt,
            String refreshTokenHash,
            Instant refreshExpiresAt
    ) {
        Instant now = Instant.now();
        Session session = sessions.get(userId);
        Map<String, Instant> accessTokens = session == null || !now.isBefore(session.refreshExpiresAt())
                ? new HashMap<>()
                : new HashMap<>(session.accessTokens());
        accessTokens.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
        accessTokens.put(accessTokenHash, accessExpiresAt);
        sessions.put(userId, new Session(accessTokens, refreshTokenHash, refreshExpiresAt));
    }

    @Override
    public synchronized boolean containsAccessTokenHash(Long userId, String accessTokenHash) {
        Session session = sessions.get(userId);
        if (session == null) {
            return false;
        }
        Instant now = Instant.now();
        session.accessTokens().entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
        return session.accessTokens().containsKey(accessTokenHash);
    }

    @Override
    public synchronized RefreshRotationResult rotate(
            Long userId,
            String currentRefreshTokenHash,
            String newAccessTokenHash,
            Instant newAccessExpiresAt,
            String newRefreshTokenHash,
            Instant newRefreshExpiresAt
    ) {
        Session current = sessions.get(userId);
        if (current == null || !Instant.now().isBefore(current.refreshExpiresAt())) {
            sessions.remove(userId);
            return RefreshRotationResult.NOT_FOUND;
        }
        if (!current.refreshTokenHash().equals(currentRefreshTokenHash)) {
            sessions.remove(userId);
            return RefreshRotationResult.MISMATCH;
        }
        save(userId, newAccessTokenHash, newAccessExpiresAt, newRefreshTokenHash, newRefreshExpiresAt);
        return RefreshRotationResult.ROTATED;
    }

    @Override
    public void delete(Long userId) {
        sessions.remove(userId);
    }

    private record Session(
            Map<String, Instant> accessTokens,
            String refreshTokenHash,
            Instant refreshExpiresAt
    ) {
    }
}
