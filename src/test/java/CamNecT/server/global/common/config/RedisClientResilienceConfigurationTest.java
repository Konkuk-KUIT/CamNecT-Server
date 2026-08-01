package CamNecT.server.global.common.config;

import io.lettuce.core.ClientOptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisClientResilienceConfigurationTest {

    @Test
    void configuresKeepAliveAutoReconnectAndFailFastWhileDisconnected() {
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setConnectTimeout(Duration.ofSeconds(1));
        RedisClientResilienceProperties resilienceProperties = new RedisClientResilienceProperties();

        var customizer = new RedisClientResilienceConfiguration()
                .redisClientOptionsBuilderCustomizer(redisProperties, resilienceProperties);
        ClientOptions.Builder builder = ClientOptions.builder();

        customizer.customize(builder);
        ClientOptions options = builder.build();

        assertThat(options.isAutoReconnect()).isTrue();
        assertThat(options.isPingBeforeActivateConnection()).isTrue();
        assertThat(options.getDisconnectedBehavior())
                .isEqualTo(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
        assertThat(options.getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(options.getSocketOptions().getKeepAlive().isEnabled()).isTrue();
        assertThat(options.getSocketOptions().getKeepAlive().getIdle()).isEqualTo(Duration.ofSeconds(30));
        assertThat(options.getSocketOptions().getKeepAlive().getInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(options.getSocketOptions().getKeepAlive().getCount()).isEqualTo(3);
    }
}
