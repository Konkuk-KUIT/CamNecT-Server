package CamNecT.server.global.common.util;

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.script.ScriptingException;
import org.springframework.data.redis.serializer.SerializationException;

import java.util.concurrent.TimeoutException;

public final class RedisFailureDetector {

    private static final int MAX_CAUSE_DEPTH = 20;

    private RedisFailureDetector() {
    }

    public static boolean isRedisFailure(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        boolean redisContext = false;
        boolean connectionFailure = false;
        boolean timeout = false;

        while (current != null && depth++ < MAX_CAUSE_DEPTH) {
            if (current instanceof RedisCommandExecutionException
                    || current instanceof ScriptingException
                    || current instanceof SerializationException) {
                return false;
            }

            redisContext |= current instanceof RedisSystemException
                    || current instanceof RedisConnectionFailureException
                    || current instanceof RedisConnectionException
                    || current instanceof RedisCommandTimeoutException;
            connectionFailure |= current instanceof RedisConnectionFailureException
                    || current instanceof RedisConnectionException;
            timeout |= current instanceof TimeoutException
                    || current instanceof RedisCommandTimeoutException;
            current = current.getCause();
        }

        return connectionFailure || (redisContext && timeout);
    }
}
