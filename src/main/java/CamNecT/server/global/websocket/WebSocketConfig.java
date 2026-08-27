package CamNecT.server.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final ChatStompInterceptor chatStompInterceptor;
    private final ChatStompErrorHandler chatStompErrorHandler;
    private TaskScheduler messageBrokerTaskScheduler;

    @Autowired
    public void setMessageBrokerTaskScheduler(
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler taskScheduler
    ) {
        this.messageBrokerTaskScheduler = taskScheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub", "/queue", "/room")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(messageBrokerTaskScheduler);    //해당 주소를 구독하고 잇는 클라이언트들에게 메세지 전달
        registry.setApplicationDestinationPrefixes("/pub", "/send"); //클라이언트에서 보낸 메세지를 받을 prefix
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setErrorHandler(chatStompErrorHandler);

        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns(
                        "https://camnect.site",
                        "https://www.camnect.site",
                        "https://camnect-web.vercel.app",
                        "http://localhost:5173"
                )
                .withSockJS();

        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns(
                        "https://camnect.site",
                        "https://www.camnect.site",
                        "https://camnect-web.vercel.app",
                        "http://localhost:5173"
                );
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatStompInterceptor);
    }
}
