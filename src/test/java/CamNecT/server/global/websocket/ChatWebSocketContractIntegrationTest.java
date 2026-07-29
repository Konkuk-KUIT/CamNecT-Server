package CamNecT.server.global.websocket;

import CamNecT.server.domain.chat.dto.message.ChatMessageAckResponseDto;
import CamNecT.server.domain.chat.dto.message.ChatMessageResponseDto;
import CamNecT.server.domain.chat.dto.message.ChatMessageSendRequestDto;
import CamNecT.server.domain.chat.dto.message.ChatSocketErrorResponse;
import CamNecT.server.domain.chat.model.ChatRequest;
import CamNecT.server.domain.chat.model.ChatRoom;
import CamNecT.server.domain.chat.repository.ChatRepository;
import CamNecT.server.domain.chat.repository.ChatRequestRepository;
import CamNecT.server.domain.chat.repository.ChatRoomRepository;
import CamNecT.server.domain.chat.service.ChatPresenceService;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.jwt.util.JwtUtil;
import CamNecT.server.global.jwt.service.TokenSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatWebSocketContractIntegrationTest {

    private static final long MESSAGE_TIMEOUT_SECONDS = 10;

    @LocalServerPort int port;
    @Autowired UserRepository userRepository;
    @Autowired ChatRequestRepository chatRequestRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired ChatRepository chatRepository;
    @Autowired ChatPresenceService presenceService;
    @Autowired JwtUtil jwtUtil;
    @Autowired TokenSessionService tokenSessionService;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void websocketSendReturnsAckBroadcastsOnceAndRoutesValidationError() throws Exception {
        Fixture fixture = createFixture();
        String token = jwtUtil.generateAccessToken(fixture.senderId(), UserRole.USER);
        String refreshToken = jwtUtil.generateRefreshToken(fixture.senderId(), UserRole.USER);
        tokenSessionService.create(fixture.senderId(), token, refreshToken);

        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());

        StompSession session = null;
        try {
            StompHeaders connectHeaders = new StompHeaders();
            connectHeaders.add("Authorization", "Bearer " + token);
            session = client.connectAsync(
                    "ws://localhost:" + port + "/ws-stomp",
                    new WebSocketHttpHeaders(),
                    connectHeaders,
                    new StompSessionHandlerAdapter() {}
            ).get(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            BlockingQueue<ChatMessageAckResponseDto> ackQueue = new LinkedBlockingQueue<>();
            BlockingQueue<ChatSocketErrorResponse> errorQueue = new LinkedBlockingQueue<>();
            BlockingQueue<ChatMessageResponseDto> roomQueue = new LinkedBlockingQueue<>();

            subscribe(session, "/user/queue/chat-acks",
                    queueingHandler(ChatMessageAckResponseDto.class, ackQueue));
            subscribe(session, "/user/queue/chat-errors",
                    queueingHandler(ChatSocketErrorResponse.class, errorQueue));
            subscribe(session, "/sub/chat/room/" + fixture.roomId(),
                    queueingHandler(ChatMessageResponseDto.class, roomQueue));
            assertThat(awaitRoomPresence(fixture)).isTrue();

            String clientMessageId = UUID.randomUUID().toString();
            ChatMessageSendRequestDto request = new ChatMessageSendRequestDto(
                    fixture.roomId(), "websocket contract message", clientMessageId);

            session.send("/pub/chat/message", request);

            ChatMessageAckResponseDto firstAck = ackQueue.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            ChatMessageResponseDto broadcast = roomQueue.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(firstAck).isNotNull();
            assertThat(firstAck.duplicate()).isFalse();
            assertThat(firstAck.clientMessageId()).isEqualTo(clientMessageId);
            assertThat(broadcast).isNotNull();
            assertThat(broadcast.getMessageId()).isEqualTo(firstAck.messageId());
            assertThat(broadcast.getClientMessageId()).isEqualTo(clientMessageId);

            session.send("/pub/chat/message", request);

            ChatMessageAckResponseDto duplicateAck = ackQueue.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(duplicateAck).isNotNull();
            assertThat(duplicateAck.duplicate()).isTrue();
            assertThat(duplicateAck.messageId()).isEqualTo(firstAck.messageId());
            assertThat(roomQueue.poll(500, TimeUnit.MILLISECONDS)).isNull();
            assertThat(chatRepository.countByRoom_IdAndSender_UserIdAndClientMessageId(
                    fixture.roomId(), fixture.senderId(), clientMessageId)).isEqualTo(1L);

            String invalidClientMessageId = UUID.randomUUID().toString();
            session.send("/pub/chat/message",
                    new ChatMessageSendRequestDto(fixture.roomId(), "", invalidClientMessageId));

            ChatSocketErrorResponse validationError =
                    errorQueue.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(validationError).isNotNull();
            assertThat(validationError.type()).isEqualTo("ERROR");
            assertThat(validationError.status()).isEqualTo(400);
            assertThat(validationError.code()).isEqualTo(48003);
            assertThat(validationError.operation()).isEqualTo("SEND_MESSAGE");
            assertThat(validationError.roomId()).isEqualTo(fixture.roomId());
            assertThat(validationError.clientMessageId()).isEqualTo(invalidClientMessageId);

            session.send("/pub/chat/message",
                    new ChatMessageSendRequestDto(fixture.roomId(), "valid content", "not-a-uuid"));

            ChatSocketErrorResponse idError = errorQueue.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(idError).isNotNull();
            assertThat(idError.status()).isEqualTo(400);
            assertThat(idError.code()).isEqualTo(48006);
            assertThat(idError.operation()).isEqualTo("SEND_MESSAGE");
            assertThat(idError.roomId()).isEqualTo(fixture.roomId());
            assertThat(idError.clientMessageId()).isEqualTo("not-a-uuid");
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            client.stop();
        }
    }

    private void subscribe(
            StompSession session,
            String destination,
            StompFrameHandler handler
    ) {
        session.subscribe(destination, handler);
    }

    private boolean awaitRoomPresence(Fixture fixture) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(MESSAGE_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (presenceService.isPresent(fixture.roomId(), fixture.senderId())) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    private <T> StompFrameHandler queueingHandler(Class<T> payloadType, BlockingQueue<T> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add(payloadType.cast(payload));
            }
        };
    }

    private Fixture createFixture() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Users sender = userRepository.save(Users.builder()
                    .username("ws-sender-" + suffix)
                    .passwordHash("password")
                    .name("sender")
                    .status(UserStatus.ACTIVE)
                    .build());
            Users receiver = userRepository.save(Users.builder()
                    .username("ws-receiver-" + suffix)
                    .passwordHash("password")
                    .name("receiver")
                    .status(UserStatus.ACTIVE)
                    .build());

            ChatRequest request = chatRequestRepository.save(ChatRequest.builder()
                    .requester(sender)
                    .receiver(receiver)
                    .requestInterest(List.of())
                    .content("request")
                    .type(ChatRequest.RequestType.COFFEE_CHAT)
                    .build());
            request.accept();

            ChatRoom room = chatRoomRepository.save(ChatRoom.createRoom(request, sender, receiver));
            return new Fixture(room.getId(), sender.getUserId());
        });
    }

    private record Fixture(Long roomId, Long senderId) {}
}
