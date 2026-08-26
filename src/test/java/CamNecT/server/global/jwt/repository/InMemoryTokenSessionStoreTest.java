package CamNecT.server.global.jwt.repository;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTokenSessionStoreTest {

    @Test
    void newSavePrunesOtherExpiredSessionsForTheUser() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-26T00:00:00Z"));
        InMemoryTokenSessionStore store = new InMemoryTokenSessionStore(clock);
        store.save(
                1L, "expired-session", "old-access", clock.instant().plusSeconds(30),
                "old-refresh", clock.instant().plusSeconds(60)
        );
        clock.advanceSeconds(61);

        store.save(
                1L, "active-session", "new-access", clock.instant().plusSeconds(30),
                "new-refresh", clock.instant().plusSeconds(60)
        );

        assertThat(sessionCount(store, 1L)).isEqualTo(1);
        assertThat(store.containsAccessTokenHash(1L, "expired-session", "old-access")).isFalse();
        assertThat(store.containsAccessTokenHash(1L, "active-session", "new-access")).isTrue();
    }

    @Test
    void accessLookupPrunesSessionWhoseRefreshLifetimeEnded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-26T00:00:00Z"));
        InMemoryTokenSessionStore store = new InMemoryTokenSessionStore(clock);
        store.save(
                1L, "expired-session", "access-hash", clock.instant().plusSeconds(120),
                "refresh-hash", clock.instant().plusSeconds(60)
        );
        clock.advanceSeconds(61);

        assertThat(store.containsAccessTokenHash(1L, "expired-session", "access-hash")).isFalse();
        assertThat(sessionCount(store, 1L)).isZero();
    }

    @SuppressWarnings("unchecked")
    private int sessionCount(InMemoryTokenSessionStore store, Long userId) {
        ConcurrentHashMap<Long, Map<String, ?>> sessions =
                (ConcurrentHashMap<Long, Map<String, ?>>) ReflectionTestUtils.getField(store, "userSessions");
        Map<String, ?> userSessions = sessions == null ? null : sessions.get(userId);
        return userSessions == null ? 0 : userSessions.size();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
