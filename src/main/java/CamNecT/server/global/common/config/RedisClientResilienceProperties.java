package CamNecT.server.global.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.redis.client")
public class RedisClientResilienceProperties {

    private boolean autoReconnect = true;
    private boolean rejectCommandsWhileDisconnected = true;
    private boolean keepAliveEnabled = true;
    private Duration keepAliveIdle = Duration.ofSeconds(30);
    private Duration keepAliveInterval = Duration.ofSeconds(10);
    private int keepAliveCount = 3;
}
