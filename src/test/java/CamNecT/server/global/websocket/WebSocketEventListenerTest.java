package CamNecT.server.global.websocket;

import CamNecT.server.domain.chat.dto.message.ChatSocketErrorResponse;
import CamNecT.server.domain.chat.service.ChatPresenceService;
import CamNecT.server.domain.chat.service.ChatService;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

class WebSocketEventListenerTest {

    private final ChatPresenceService presenceService = mock(ChatPresenceService.class);
    private final ChatService chatService = mock(ChatService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ChatSocketErrorMapper errorMapper = mock(ChatSocketErrorMapper.class);
    private final ChatSocketErrorPublisher errorPublisher = mock(ChatSocketErrorPublisher.class);
    private final WebSocketEventListener listener = new WebSocketEventListener(
            presenceService, chatService, userRepository, errorMapper, errorPublisher);

    @Test
    void readFailureDoesNotLeaveAFalsePresenceAndIsDeliveredToOriginatingSession() {
        Users user = Users.builder().userId(1L).build();
        CustomException failure = new CustomException(ErrorCode.INTERNAL_ERROR);
        ChatSocketErrorResponse response = new ChatSocketErrorResponse(
                "ERROR", 500, 50000, "서버 내부 오류가 발생했습니다.",
                "SUBSCRIBE", 99L, null);
        Message<byte[]> message = subscribeMessage();
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(failure).when(chatService).markAllAsRead(99L, user);
        when(errorMapper.map(org.mockito.ArgumentMatchers.eq(failure),
                org.mockito.ArgumentMatchers.any(StompHeaderAccessor.class),
                org.mockito.ArgumentMatchers.isNull())).thenReturn(response);

        listener.handleSessionSubscribeEvent(new SessionSubscribeEvent(this, message));

        verifyNoInteractions(presenceService);
        verify(errorPublisher).sendToSession(1L, "session-a", response);
    }

    @Test
    void successfulReadValidationPrecedesPresenceRegistration() {
        Users user = Users.builder().userId(1L).build();
        Message<byte[]> message = subscribeMessage();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        listener.handleSessionSubscribeEvent(new SessionSubscribeEvent(this, message));

        var order = inOrder(chatService, presenceService);
        order.verify(chatService).markAllAsRead(99L, user);
        order.verify(presenceService).enter(99L, 1L, "session-a", "subscription-a");
        verifyNoInteractions(errorPublisher);
    }

    private Message<byte[]> subscribeMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/sub/chat/room/99");
        accessor.setSessionId("session-a");
        accessor.setSubscriptionId("subscription-a");
        accessor.setSessionAttributes(new HashMap<>(Map.of("userId", 1L)));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
