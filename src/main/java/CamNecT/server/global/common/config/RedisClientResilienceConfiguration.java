package CamNecT.server.global.common.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientOptionsBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RedisClientResilienceProperties.class)
public class RedisClientResilienceConfiguration {

    @Bean
    LettuceClientOptionsBuilderCustomizer redisClientOptionsBuilderCustomizer(
            RedisProperties redisProperties,
            RedisClientResilienceProperties resilienceProperties
    ) {
        Duration connectTimeout = redisProperties.getConnectTimeout() == null
                ? Duration.ofSeconds(1)
                : redisProperties.getConnectTimeout();

        SocketOptions.KeepAliveOptions keepAliveOptions = SocketOptions.KeepAliveOptions.builder()
                .enable(resilienceProperties.isKeepAliveEnabled())
                .idle(resilienceProperties.getKeepAliveIdle())
                .interval(resilienceProperties.getKeepAliveInterval())
                .count(resilienceProperties.getKeepAliveCount())
                .build();

        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(connectTimeout)
                .keepAlive(keepAliveOptions)
                .tcpNoDelay(true)
                .build();

        return builder -> builder
                .autoReconnect(resilienceProperties.isAutoReconnect())
                .pingBeforeActivateConnection(true)
                .disconnectedBehavior(resilienceProperties.isRejectCommandsWhileDisconnected()
                        ? ClientOptions.DisconnectedBehavior.REJECT_COMMANDS
                        : ClientOptions.DisconnectedBehavior.ACCEPT_COMMANDS)
                .socketOptions(socketOptions);
    }
}
