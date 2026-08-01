package CamNecT.server.global.websocket;

import CamNecT.server.domain.chat.dto.message.ChatMessageSendRequestDto;
import CamNecT.server.domain.chat.dto.message.ChatSocketErrorResponse;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.CoffeeChatErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisCommandTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSocketErrorMapperTest {

    private final ChatSocketErrorMapper mapper = new ChatSocketErrorMapper(new ObjectMapper());

    @Test
    void mapsDomainErrorWithMessageCorrelationContext() {
        ChatMessageSendRequestDto request = new ChatMessageSendRequestDto(
                99L,
                "hello",
                "0e9e31aa-99e7-4c58-90d8-f939b56fd234"
        );

        ChatSocketErrorResponse response = mapper.map(
                new CustomException(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED),
                sendMessage(request)
        );

        assertThat(response.type()).isEqualTo("ERROR");
        assertThat(response.status()).isEqualTo(403);
        assertThat(response.code()).isEqualTo(48302);
        assertThat(response.operation()).isEqualTo("SEND_MESSAGE");
        assertThat(response.roomId()).isEqualTo(99L);
        assertThat(response.clientMessageId()).isEqualTo(request.clientMessageId());
    }

    @Test
    void mapsMessageConversionFailureToBadRequestWithoutLeakingDetails() {
        ChatSocketErrorResponse response = mapper.map(
                new MessageConversionException("raw parser detail"),
                sendMessage("not-json")
        );

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.code()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        assertThat(response.message()).isEqualTo(ErrorCode.BAD_REQUEST.getMessage());
        assertThat(response.operation()).isEqualTo("SEND_MESSAGE");
    }

    @Test
    void mapsUnexpectedFailureToInternalError() {
        ChatSocketErrorResponse response = mapper.map(
                new IllegalStateException("database password must not leak"),
                sendMessage(new byte[0])
        );

        assertThat(response.status()).isEqualTo(500);
        assertThat(response.code()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
        assertThat(response.message()).doesNotContain("password");
    }

    @Test
    void mapsRedisFailureToServiceUnavailable() {
        ChatSocketErrorResponse response = mapper.map(
                new RedisCommandTimeoutException("Command timed out"),
                sendMessage(new byte[0])
        );

        assertThat(response.status()).isEqualTo(503);
        assertThat(response.code()).isEqualTo(ErrorCode.REDIS_UNAVAILABLE.getCode());
        assertThat(response.message()).doesNotContainIgnoringCase("redis");
    }

    private Message<?> sendMessage(Object payload) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/pub/chat/message");
        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }
}
