package CamNecT.server.global.websocket;

import CamNecT.server.domain.chat.dto.message.ChatMessageSendRequestDto;
import CamNecT.server.domain.chat.dto.message.ChatSocketErrorResponse;
import CamNecT.server.domain.chat.dto.message.ChatSocketOperation;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.BaseErrorCode;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.CoffeeChatErrorCode;
import CamNecT.server.global.common.util.RedisFailureDetector;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ChatSocketErrorMapper {

    private static final int MAX_CAUSE_DEPTH = 20;

    private final ObjectMapper objectMapper;

    public ChatSocketErrorResponse map(Throwable throwable, Message<?> message) {
        StompHeaderAccessor accessor = message == null
                ? null
                : MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        Object payload = message == null ? null : message.getPayload();
        return map(throwable, accessor, payload);
    }

    public ChatSocketErrorResponse map(
            Throwable throwable,
            StompHeaderAccessor accessor,
            Object payload
    ) {
        ChatMessageSendRequestDto request = extractRequest(throwable, payload);
        String destination = accessor == null ? null : accessor.getDestination();
        ChatSocketOperation operation = resolveOperation(accessor, destination);

        Long roomId = request == null ? extractRoomId(destination) : request.roomId();
        String clientMessageId = request == null ? nativeClientMessageId(accessor) : request.clientMessageId();
        BaseErrorCode errorCode = resolveErrorCode(throwable);

        return ChatSocketErrorResponse.of(
                errorCode,
                operation.name(),
                roomId,
                clientMessageId
        );
    }

    private BaseErrorCode resolveErrorCode(Throwable throwable) {
        if (RedisFailureDetector.isRedisFailure(throwable)) {
            return ErrorCode.REDIS_UNAVAILABLE;
        }
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSE_DEPTH) {
            if (current instanceof CustomException customException) {
                return customException.getErrorCode();
            }
            if (current instanceof MethodArgumentNotValidException validationException) {
                return resolveValidationErrorCode(validationException);
            }
            if (current instanceof MessageConversionException
                    || current instanceof ConstraintViolationException) {
                return ErrorCode.BAD_REQUEST;
            }
            current = current.getCause();
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    private BaseErrorCode resolveValidationErrorCode(MethodArgumentNotValidException exception) {
        if (exception.getBindingResult().hasFieldErrors("clientMessageId")) {
            return CoffeeChatErrorCode.INVALID_CLIENT_MESSAGE_ID;
        }
        if (exception.getBindingResult().hasFieldErrors("content")) {
            return CoffeeChatErrorCode.INVALID_CHAT_CONTENT;
        }
        return ErrorCode.BAD_REQUEST;
    }

    private ChatMessageSendRequestDto extractRequest(Throwable throwable, Object payload) {
        if (payload instanceof ChatMessageSendRequestDto request) return request;

        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSE_DEPTH) {
            if (current instanceof MethodArgumentNotValidException validationException
                    && validationException.getBindingResult() != null
                    && validationException.getBindingResult().getTarget() instanceof ChatMessageSendRequestDto request) {
                return request;
            }
            current = current.getCause();
        }

        byte[] bytes = payloadBytes(payload);
        if (bytes == null || bytes.length == 0) return null;
        try {
            return objectMapper.readValue(bytes, ChatMessageSendRequestDto.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] payloadBytes(Object payload) {
        if (payload instanceof byte[] bytes) return bytes;
        if (payload instanceof String string) return string.getBytes(StandardCharsets.UTF_8);
        return null;
    }

    private ChatSocketOperation resolveOperation(StompHeaderAccessor accessor, String destination) {
        StompCommand command = accessor == null ? null : accessor.getCommand();
        if (command == StompCommand.CONNECT) return ChatSocketOperation.CONNECT;
        if (command == StompCommand.SUBSCRIBE) return ChatSocketOperation.SUBSCRIBE;
        if (destination == null) return ChatSocketOperation.UNKNOWN;
        if (destination.endsWith("/chat/message")) return ChatSocketOperation.SEND_MESSAGE;
        if (destination.contains("/chat/room/") && destination.endsWith("/leave")) {
            return ChatSocketOperation.LEAVE_ROOM;
        }
        return ChatSocketOperation.UNKNOWN;
    }

    private Long extractRoomId(String destination) {
        if (destination == null) return null;

        String[] segments = destination.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            if (segments[i].isBlank() || "leave".equals(segments[i])) continue;
            try {
                return Long.valueOf(segments[i]);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String nativeClientMessageId(StompHeaderAccessor accessor) {
        return accessor == null ? null : accessor.getFirstNativeHeader("clientMessageId");
    }
}
