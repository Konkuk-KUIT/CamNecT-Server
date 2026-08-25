package CamNecT.server.global.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketOrderingConfigTest {

    @Test
    void preservesInboundAndBrokerPublishOrder() {
        WebSocketConfig config = new WebSocketConfig(
                mock(ChatStompInterceptor.class),
                mock(ChatStompErrorHandler.class)
        );
        MessageBrokerRegistry brokerRegistry = mock(MessageBrokerRegistry.class, RETURNS_DEEP_STUBS);
        StompEndpointRegistry endpointRegistry = mock(StompEndpointRegistry.class, RETURNS_DEEP_STUBS);

        config.configureMessageBroker(brokerRegistry);
        config.registerStompEndpoints(endpointRegistry);

        verify(brokerRegistry).setPreservePublishOrder(true);
        verify(endpointRegistry).setPreserveReceiveOrder(true);
    }
}
