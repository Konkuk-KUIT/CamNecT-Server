package CamNecT.server.global.jwt.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisTokenSessionStoreTest {

    @Test
    void saveAtomicallyPrunesExpiredSessionPrefixesBeforeExtendingUserKeyTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        RedisTokenSessionStore store = new RedisTokenSessionStore(
                redisTemplate,
                "test:session:",
                Clock.fixed(now, ZoneOffset.UTC)
        );
        ArgumentCaptor<DefaultRedisScript<Long>> script = defaultRedisScriptCaptor();

        store.save(
                1L,
                "session-id",
                "access-hash",
                now.plusSeconds(30),
                "refresh-hash",
                now.plusSeconds(60)
        );

        verify(redisTemplate).execute(
                script.capture(),
                eq(List.of("test:session:v2:{1}")),
                eq("s:session-id:a:access-hash"),
                eq(Long.toString(now.plusSeconds(30).toEpochMilli())),
                eq("s:session-id:r"),
                eq("refresh-hash"),
                eq("s:session-id:re"),
                eq(Long.toString(now.plusSeconds(60).toEpochMilli())),
                eq(Long.toString(now.toEpochMilli()))
        );
        assertThat(script.getValue().getScriptAsString()).contains(
                "string.sub(field, -3) == ':re'",
                "tonumber(refreshExpiresAt) <= tonumber(ARGV[7])",
                "expiredPrefixes[prefix]",
                "redis.call('HDEL', KEYS[1], field)",
                "if tonumber(ARGV[6]) <= tonumber(ARGV[7]) then"
        );
    }

    @Test
    void rotateAtomicallyPrunesExpiredSessionsUsingInjectedClock() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        RedisTokenSessionStore store = new RedisTokenSessionStore(
                redisTemplate,
                "test:session:",
                Clock.fixed(now, ZoneOffset.UTC)
        );
        ArgumentCaptor<DefaultRedisScript<Long>> script = defaultRedisScriptCaptor();

        assertThat(store.rotate(
                1L,
                "session-id",
                "old-refresh-hash",
                "new-access-hash",
                now.plusSeconds(30),
                "new-refresh-hash",
                now.plusSeconds(60)
        )).isEqualTo(TokenSessionStore.RefreshRotationResult.NOT_FOUND);

        verify(redisTemplate).execute(
                script.capture(),
                eq(List.of("test:session:v2:{1}")),
                eq("s:session-id:"),
                eq("s:session-id:r"),
                eq("s:session-id:re"),
                eq("old-refresh-hash"),
                eq("s:session-id:a:new-access-hash"),
                eq(Long.toString(now.plusSeconds(30).toEpochMilli())),
                eq("new-refresh-hash"),
                eq(Long.toString(now.plusSeconds(60).toEpochMilli())),
                eq(Long.toString(now.toEpochMilli()))
        );
        assertThat(script.getValue().getScriptAsString()).contains(
                "local function pruneExpiredSessions(now)",
                "pruneExpiredSessions(ARGV[9])",
                "expiredPrefixes[prefix]"
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<DefaultRedisScript<Long>> defaultRedisScriptCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(DefaultRedisScript.class);
    }
}
