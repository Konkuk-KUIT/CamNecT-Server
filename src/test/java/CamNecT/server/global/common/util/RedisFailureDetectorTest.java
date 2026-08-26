package CamNecT.server.global.common.util;

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.script.ScriptingException;
import org.springframework.data.redis.serializer.SerializationException;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class RedisFailureDetectorTest {

    @Test
    void detectsTranslatedAndNativeConnectionFailures() {
        assertThat(RedisFailureDetector.isRedisFailure(
                new RedisConnectionFailureException("Redis connection failed")
        )).isTrue();
        assertThat(RedisFailureDetector.isRedisFailure(
                new RuntimeException(new RedisConnectionException("Unable to connect"))
        )).isTrue();
    }

    @Test
    void detectsRedisCommandTimeoutsThroughSpringWrappers() {
        assertThat(RedisFailureDetector.isRedisFailure(
                new QueryTimeoutException("Redis command timed out", new RedisCommandTimeoutException())
        )).isTrue();
        assertThat(RedisFailureDetector.isRedisFailure(
                new RedisSystemException("Redis exception", new TimeoutException("timed out"))
        )).isTrue();
    }

    @Test
    void doesNotTreatGenericQueryTimeoutAsRedisOutage() {
        assertThat(RedisFailureDetector.isRedisFailure(
                new QueryTimeoutException("Database query timed out", new TimeoutException("timed out"))
        )).isFalse();
    }

    @Test
    void doesNotTreatCommandErrorsAsConnectionOutage() {
        assertThat(RedisFailureDetector.isRedisFailure(
                new RedisSystemException(
                        "Redis exception",
                        new RedisCommandExecutionException("WRONGTYPE Operation against a key")
                )
        )).isFalse();
        assertThat(RedisFailureDetector.isRedisFailure(
                new RedisConnectionException(
                        "Unable to connect",
                        new RedisCommandExecutionException("WRONGPASS invalid username-password pair")
                )
        )).isFalse();
    }

    @Test
    void doesNotTreatSystemScriptOrSerializationErrorsAsOutage() {
        assertThat(RedisFailureDetector.isRedisFailure(
                new RedisSystemException("Redis exception", new IllegalStateException("unexpected result"))
        )).isFalse();
        assertThat(RedisFailureDetector.isRedisFailure(
                new ScriptingException("Lua execution failed")
        )).isFalse();
        assertThat(RedisFailureDetector.isRedisFailure(
                new SerializationException("Cannot serialize value")
        )).isFalse();
    }
}
