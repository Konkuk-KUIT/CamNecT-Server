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

    private final ConcurrentHashMap<Long, Map<String, Session>> userSessions = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(
            Long userId,
            String sessionId,
            String accessTokenHash,
            Instant accessExpiresAt,
            String refreshTokenHash,
            Instant refreshExpiresAt
    ) {
        Instant now = Instant.now();
        Map<String, Session> sessions = userSessions.computeIfAbsent(userId, ignored -> new HashMap<>());
        Session session = sessions.get(sessionId);
        Map<String, Instant> accessTokens = session == null || !now.isBefore(session.refreshExpiresAt())
                ? new HashMap<>()
                : new HashMap<>(session.accessTokens());
        accessTokens.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
        accessTokens.put(accessTokenHash, accessExpiresAt);
        sessions.put(sessionId, new Session(accessTokens, refreshTokenHash, refreshExpiresAt));
    }

    @Override
    public synchronized boolean containsAccessTokenHash(Long userId, String sessionId, String accessTokenHash) {
        Map<String, Session> sessions = userSessions.get(userId);
        Session session = sessions == null ? null : sessions.get(sessionId);
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
            String sessionId,
            String currentRefreshTokenHash,
            String newAccessTokenHash,
            Instant newAccessExpiresAt,
            String newRefreshTokenHash,
            Instant newRefreshExpiresAt
    ) {
        Map<String, Session> sessions = userSessions.get(userId);
        Session current = sessions == null ? null : sessions.get(sessionId);
        if (current == null || !Instant.now().isBefore(current.refreshExpiresAt())) {
            removeSession(userId, sessionId);
            return RefreshRotationResult.NOT_FOUND;
        }
        if (!current.refreshTokenHash().equals(currentRefreshTokenHash)) {
            removeSession(userId, sessionId);
            return RefreshRotationResult.MISMATCH;
        }
        save(userId, sessionId, newAccessTokenHash, newAccessExpiresAt, newRefreshTokenHash, newRefreshExpiresAt);
        return RefreshRotationResult.ROTATED;
    }

    @Override
    public synchronized void deleteSession(Long userId, String sessionId) {
        removeSession(userId, sessionId);
    }

    @Override
    public void deleteAll(Long userId) {
        userSessions.remove(userId);
    }

    private void removeSession(Long userId, String sessionId) {
        Map<String, Session> sessions = userSessions.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
            userSessions.remove(userId);
        }
    }

    private record Session(
            Map<String, Instant> accessTokens,
            String refreshTokenHash,
            Instant refreshExpiresAt
    ) {
    }
}
