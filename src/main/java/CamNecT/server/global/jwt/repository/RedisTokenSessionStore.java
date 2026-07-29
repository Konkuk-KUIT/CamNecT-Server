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
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[5])
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
            local latest = redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES')
            redis.call('PEXPIREAT', KEYS[1], latest[2])
            redis.call('SET', KEYS[2], ARGV[3], 'PXAT', ARGV[4])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> FIND_ACCESS_SCRIPT = new DefaultRedisScript<>("""
            local expiresAt = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if not expiresAt then
                return 0
            end
            if tonumber(expiresAt) <= tonumber(ARGV[2]) then
                redis.call('ZREM', KEYS[1], ARGV[1])
                return 0
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[2])
            if not current then
                return 0
            end
            if current ~= ARGV[1] then
                redis.call('DEL', KEYS[1], KEYS[2])
                return -1
            end
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[6])
            redis.call('ZADD', KEYS[1], ARGV[3], ARGV[2])
            local latest = redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES')
            redis.call('PEXPIREAT', KEYS[1], latest[2])
            redis.call('SET', KEYS[2], ARGV[4], 'PXAT', ARGV[5])
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
            String accessTokenHash,
            Instant accessExpiresAt,
            String refreshTokenHash,
            Instant refreshExpiresAt
    ) {
        redisTemplate.execute(
                SAVE_SCRIPT,
                keys(userId),
                accessTokenHash,
                epochMillis(accessExpiresAt),
                refreshTokenHash,
                epochMillis(refreshExpiresAt),
                epochMillis(Instant.now())
        );
    }

    @Override
    public boolean containsAccessTokenHash(Long userId, String accessTokenHash) {
        Long result = redisTemplate.execute(
                FIND_ACCESS_SCRIPT,
                List.of(accessKey(userId)),
                accessTokenHash,
                epochMillis(Instant.now())
        );
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public RefreshRotationResult rotate(
            Long userId,
            String currentRefreshTokenHash,
            String newAccessTokenHash,
            Instant newAccessExpiresAt,
            String newRefreshTokenHash,
            Instant newRefreshExpiresAt
    ) {
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                keys(userId),
                currentRefreshTokenHash,
                newAccessTokenHash,
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
    public void delete(Long userId) {
        redisTemplate.delete(keys(userId));
    }

    private List<String> keys(Long userId) {
        return List.of(accessKey(userId), refreshKey(userId));
    }

    private String accessKey(Long userId) {
        return keyPrefix + "{" + userId + "}:access";
    }

    private String refreshKey(Long userId) {
        return keyPrefix + "{" + userId + "}:refresh";
    }

    private String epochMillis(Instant instant) {
        return Long.toString(instant.toEpochMilli());
    }
}
