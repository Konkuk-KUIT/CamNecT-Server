package CamNecT.server.global.jwt.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "app.auth.token-store", havingValue = "redis", matchIfMissing = true)
public class RedisTokenSessionStore implements TokenSessionStore {

    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1],
                ARGV[1], ARGV[2],
                ARGV[3], ARGV[4],
                ARGV[5], ARGV[6])
            local currentExpireAt = redis.call('PEXPIRETIME', KEYS[1])
            if currentExpireAt < tonumber(ARGV[6]) then
                redis.call('PEXPIREAT', KEYS[1], ARGV[6])
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> FIND_ACCESS_SCRIPT = new DefaultRedisScript<>("""
            local expiresAt = redis.call('HGET', KEYS[1], ARGV[1])
            if not expiresAt then
                return 0
            end
            if tonumber(expiresAt) <= tonumber(ARGV[2]) then
                redis.call('HDEL', KEYS[1], ARGV[1])
                return 0
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local function deleteSession(prefix)
                local fields = redis.call('HKEYS', KEYS[1])
                for _, field in ipairs(fields) do
                    if string.sub(field, 1, string.len(prefix)) == prefix then
                        redis.call('HDEL', KEYS[1], field)
                    end
                end
                if redis.call('HLEN', KEYS[1]) == 0 then
                    redis.call('DEL', KEYS[1])
                end
            end

            local current = redis.call('HGET', KEYS[1], ARGV[2])
            local refreshExpiresAt = redis.call('HGET', KEYS[1], ARGV[3])
            if not current or not refreshExpiresAt or tonumber(refreshExpiresAt) <= tonumber(ARGV[9]) then
                deleteSession(ARGV[1])
                return 0
            end
            if current ~= ARGV[4] then
                deleteSession(ARGV[1])
                return -1
            end

            local fields = redis.call('HKEYS', KEYS[1])
            local accessPrefix = ARGV[1] .. 'a:'
            for _, field in ipairs(fields) do
                if string.sub(field, 1, string.len(accessPrefix)) == accessPrefix then
                    local accessExpiresAt = redis.call('HGET', KEYS[1], field)
                    if accessExpiresAt and tonumber(accessExpiresAt) <= tonumber(ARGV[9]) then
                        redis.call('HDEL', KEYS[1], field)
                    end
                end
            end

            redis.call('HSET', KEYS[1],
                ARGV[5], ARGV[6],
                ARGV[2], ARGV[7],
                ARGV[3], ARGV[8])
            local currentExpireAt = redis.call('PEXPIRETIME', KEYS[1])
            if currentExpireAt < tonumber(ARGV[8]) then
                redis.call('PEXPIREAT', KEYS[1], ARGV[8])
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> DELETE_SESSION_SCRIPT = new DefaultRedisScript<>("""
            local fields = redis.call('HKEYS', KEYS[1])
            for _, field in ipairs(fields) do
                if string.sub(field, 1, string.len(ARGV[1])) == ARGV[1] then
                    redis.call('HDEL', KEYS[1], field)
                end
            end
            if redis.call('HLEN', KEYS[1]) == 0 then
                redis.call('DEL', KEYS[1])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisTokenSessionStore(
            StringRedisTemplate redisTemplate,
            @Value("${app.auth.redis.key-prefix:camnect:auth:session:}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public void save(
            Long userId,
            String sessionId,
            String accessTokenHash,
            Instant accessExpiresAt,
            String refreshTokenHash,
            Instant refreshExpiresAt
    ) {
        redisTemplate.execute(
                SAVE_SCRIPT,
                List.of(userKey(userId)),
                accessField(sessionId, accessTokenHash),
                epochMillis(accessExpiresAt),
                refreshField(sessionId),
                refreshTokenHash,
                refreshExpiryField(sessionId),
                epochMillis(refreshExpiresAt)
        );
    }

    @Override
    public boolean containsAccessTokenHash(Long userId, String sessionId, String accessTokenHash) {
        Long result = redisTemplate.execute(
                FIND_ACCESS_SCRIPT,
                List.of(userKey(userId)),
                accessField(sessionId, accessTokenHash),
                epochMillis(Instant.now())
        );
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public RefreshRotationResult rotate(
            Long userId,
            String sessionId,
            String currentRefreshTokenHash,
            String newAccessTokenHash,
            Instant newAccessExpiresAt,
            String newRefreshTokenHash,
            Instant newRefreshExpiresAt
    ) {
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(userKey(userId)),
                sessionPrefix(sessionId),
                refreshField(sessionId),
                refreshExpiryField(sessionId),
                currentRefreshTokenHash,
                accessField(sessionId, newAccessTokenHash),
                epochMillis(newAccessExpiresAt),
                newRefreshTokenHash,
                epochMillis(newRefreshExpiresAt),
                epochMillis(Instant.now())
        );
        if (Long.valueOf(1L).equals(result)) {
            return RefreshRotationResult.ROTATED;
        }
        if (Long.valueOf(-1L).equals(result)) {
            return RefreshRotationResult.MISMATCH;
        }
        return RefreshRotationResult.NOT_FOUND;
    }

    @Override
    public void deleteSession(Long userId, String sessionId) {
        redisTemplate.execute(
                DELETE_SESSION_SCRIPT,
                List.of(userKey(userId)),
                sessionPrefix(sessionId)
        );
    }

    @Override
    public void deleteAll(Long userId) {
        redisTemplate.delete(userKey(userId));
    }

    private String userKey(Long userId) {
        return keyPrefix + "v2:{" + userId + "}";
    }

    private String sessionPrefix(String sessionId) {
        return "s:" + sessionId + ":";
    }

    private String accessField(String sessionId, String accessTokenHash) {
        return sessionPrefix(sessionId) + "a:" + accessTokenHash;
    }

    private String refreshField(String sessionId) {
        return sessionPrefix(sessionId) + "r";
    }

    private String refreshExpiryField(String sessionId) {
        return sessionPrefix(sessionId) + "re";
    }

    private String epochMillis(Instant instant) {
        return Long.toString(instant.toEpochMilli());
    }
}
