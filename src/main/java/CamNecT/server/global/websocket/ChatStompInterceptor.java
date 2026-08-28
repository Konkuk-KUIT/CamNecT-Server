package CamNecT.server.global.websocket;

import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.util.JwtUtil;
import CamNecT.server.global.jwt.model.TokenType;
import CamNecT.server.global.jwt.service.TokenSessionService;
import CamNecT.server.domain.chat.repository.ChatRoomRepository;
import CamNecT.server.global.common.response.errorcode.bydomains.CoffeeChatErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatStompInterceptor implements ChannelInterceptor {

    private static final Pattern CHAT_ROOM_LEAVE_DESTINATION = Pattern.compile(
            "^/(?:pub|send)/chat/room/[1-9]\\d*/leave$"
    );

    private final JwtUtil jwtUtil;
    private final AccountAccessGuard accountAccessGuard;
    private final TokenSessionService tokenSessionService;
    private final ChatRoomRepository chatRoomRepository;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {

            String token = extractBearer(accessor);

            try {
                jwtUtil.validateOrThrow(token);

                if (jwtUtil.getTokenType(token) != TokenType.ACCESS) {
                    throw new CustomException(AuthErrorCode.TOKEN_TYPE_NOT_ALLOWED);
                }

                Long userId = jwtUtil.getUserId(token);
                accountAccessGuard.requireActive(userId);
                String sessionId = tokenSessionService.requireActiveAccess(userId, token);
                if (accessor.getSessionAttributes() == null) {
                    throw new CustomException(AuthErrorCode.INVALID_TOKEN);
                }
                accessor.getSessionAttributes().put("userId", userId);
                accessor.getSessionAttributes().put("sessionId", sessionId);
                accessor.getSessionAttributes().put("accessTokenHash", tokenSessionService.accessTokenHash(token));

                accessor.setUser(new StompPrincipal(userId.toString()));

                log.info("소켓 연결 성공: userId = {}", userId);

            } catch (CustomException e) {
                // 토큰 검증 실패 시 소켓 연결 거부
                log.error("소켓 인증 실패: {}", e.getMessage());
                throw e;
            }
        }

        if (accessor != null && (StompCommand.SEND.equals(accessor.getCommand())
                || StompCommand.SUBSCRIBE.equals(accessor.getCommand()))) {
            Long userId = requireActiveSessionUser(accessor);
            if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                authorizeSubscription(accessor, userId);
            } else {
                authorizeSend(accessor);
            }
        }

        return message;
    }

    private Long requireActiveSessionUser(StompHeaderAccessor accessor) {
        Object userIdValue = accessor.getSessionAttributes() == null
                ? null : accessor.getSessionAttributes().get("userId");
        if (userIdValue == null) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
        try {
            Long userId = Long.valueOf(userIdValue.toString());
            accountAccessGuard.requireActive(userId);
            Object tokenHash = accessor.getSessionAttributes().get("accessTokenHash");
            Object sessionId = accessor.getSessionAttributes().get("sessionId");
            if (tokenHash == null || sessionId == null) {
                throw new CustomException(AuthErrorCode.INVALID_TOKEN);
            }
            tokenSessionService.requireActiveAccessHash(userId, sessionId.toString(), tokenHash.toString());
            return userId;
        } catch (NumberFormatException e) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN, e);
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor, Long userId) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new CustomException(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED);
        }

        if (destination.equals("/user/queue/chat-errors")
                || destination.equals("/user/queue/chat-acks")
                || destination.equals("/user/queue/notifications")) {
            return;
        }

        if (destination.startsWith("/sub/chat/room/")) {
            Long roomId = parseTrailingId(destination);
            if (!chatRoomRepository.existsAccessibleByUserId(roomId, userId)) {
                throw new CustomException(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED);
            }
            return;
        }

        if (destination.startsWith("/sub/user/") && destination.endsWith("/rooms")) {
            String rawUserId = destination.substring("/sub/user/".length(), destination.length() - "/rooms".length());
            try {
                if (!userId.equals(Long.valueOf(rawUserId))) {
                    throw new CustomException(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED);
                }
            } catch (NumberFormatException e) {
                throw new CustomException(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED, e);
            }
            return;
        }

        throw new CustomException(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED);
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if ("/pub/chat/message".equals(destination)
                || "/send/chat/message".equals(destination)
                || destination != null && CHAT_ROOM_LEAVE_DESTINATION.matcher(destination).matches()) {
            return;
        }

        throw new CustomException(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED);
    }

    private Long parseTrailingId(String destination) {
        try {
            return Long.valueOf(destination.substring(destination.lastIndexOf('/') + 1));
        } catch (NumberFormatException e) {
            throw new CustomException(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED, e);
        }
    }

    private String extractBearer(StompHeaderAccessor accessor) {
        String rawToken = accessor.getFirstNativeHeader("Authorization");

        if (rawToken == null || !rawToken.startsWith("Bearer ")) {
            throw new CustomException(AuthErrorCode.TOKEN_SHAPE_NOT_ALLOWED);
        }

        return rawToken.substring(7);
    }
}
