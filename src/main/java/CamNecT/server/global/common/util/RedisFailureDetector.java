package CamNecT.server.global.common.util;

import io.lettuce.core.RedisException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

public final class RedisFailureDetector {

    private static final int MAX_CAUSE_DEPTH = 20;

    private RedisFailureDetector() {
    }

    public static boolean isRedisFailure(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSE_DEPTH) {
            if (current instanceof RedisException
                    || current instanceof RedisConnectionFailureException
                    || current instanceof RedisSystemException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
