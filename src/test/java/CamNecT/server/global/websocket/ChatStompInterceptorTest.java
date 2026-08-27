package CamNecT.server.global.websocket;

import CamNecT.server.domain.chat.repository.ChatRoomRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.CoffeeChatErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.util.JwtUtil;
import CamNecT.server.global.jwt.service.TokenSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatStompInterceptorTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final AccountAccessGuard accountAccessGuard = mock(AccountAccessGuard.class);
    private final TokenSessionService tokenSessionService = mock(TokenSessionService.class);
    private final ChatRoomRepository chatRoomRepository = mock(ChatRoomRepository.class);
    private final MessageChannel channel = mock(MessageChannel.class);
    private final ChatStompInterceptor interceptor =
            new ChatStompInterceptor(jwtUtil, accountAccessGuard, tokenSessionService, chatRoomRepository);

    @Test
    void rejectsSubscriptionToRoomWhereUserIsNotParticipant() {
        when(chatRoomRepository.existsAccessibleByUserId(99L, 1L)).thenReturn(false);

        CustomException ex = assertThrows(CustomException.class,
                () -> interceptor.preSend(subscribe("/sub/chat/room/99"), channel));

        assertThat(ex.getErrorCode()).isEqualTo(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED);
    }

    @Test
    void rejectsAnotherUsersRoomListSubscription() {
        CustomException ex = assertThrows(CustomException.class,
                () -> interceptor.preSend(subscribe("/sub/user/2/rooms"), channel));

        assertThat(ex.getErrorCode()).isEqualTo(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED);
    }

    @Test
    void allowsOwnAccessibleSubscriptions() {
        when(chatRoomRepository.existsAccessibleByUserId(99L, 1L)).thenReturn(true);

        assertDoesNotThrow(() -> interceptor.preSend(subscribe("/sub/chat/room/99"), channel));
        assertDoesNotThrow(() -> interceptor.preSend(subscribe("/sub/user/1/rooms"), channel));
        assertDoesNotThrow(() -> interceptor.preSend(subscribe("/user/queue/chat-errors"), channel));
        assertDoesNotThrow(() -> interceptor.preSend(subscribe("/user/queue/chat-acks"), channel));
    }

    @Test
    void rejectsUnknownSubscriptionDestination() {
        CustomException ex = assertThrows(CustomException.class,
                () -> interceptor.preSend(subscribe("/sub/admin/secret"), channel));

        assertThat(ex.getErrorCode()).isEqualTo(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED);
    }

    @Test
    void rechecksAccountRestrictionOnEverySendFrame() {
        assertDoesNotThrow(() -> interceptor.preSend(send(), channel));

        verify(accountAccessGuard).requireActive(1L);
    }

    @Test
    void rejectsSendFrameWhenAccountBecameRestrictedAfterConnect() {
        doThrow(new CustomException(AuthErrorCode.ACTIVE_ACCOUNT_REQUIRED))
                .when(accountAccessGuard).requireActive(1L);

        CustomException ex = assertThrows(CustomException.class,
                () -> interceptor.preSend(send(), channel));

        assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.ACTIVE_ACCOUNT_REQUIRED);
    }

    private Message<byte[]> subscribe(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSessionAttributes(sessionAttributes());
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> send() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/chat/message");
        accessor.setSessionAttributes(sessionAttributes());
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private HashMap<String, Object> sessionAttributes() {
        return new HashMap<>(Map.of(
                "userId", 1L,
                "sessionId", "session-id",
                "accessTokenHash", "hash"
        ));
    }
}
