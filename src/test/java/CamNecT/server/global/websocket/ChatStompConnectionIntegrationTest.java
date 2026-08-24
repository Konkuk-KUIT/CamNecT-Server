package CamNecT.server.global.websocket;

import CamNecT.server.domain.chat.dto.message.ChatSocketErrorResponse;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.jwt.service.TokenSessionService;
import CamNecT.server.global.jwt.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatStompConnectionIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @LocalServerPort int port;
    @Autowired JwtUtil jwtUtil;
    @Autowired TokenSessionService tokenSessionService;
    @Autowired UserRepository userRepository;
    @Autowired ObjectMapper objectMapper;
    @Value("${jwt.secret}") String jwtSecret;

    @Test
    void tamperedAccessTokenReturnsStructuredErrorThenCloses() throws Exception {
        String token = jwtUtil.generateAccessToken(1L, UserRole.USER, sessionId());

        assertConnectRejected("Bearer " + tamperSignature(token), 401, 40100);
    }

    @Test
    void expiredAccessTokenReturnsStructuredErrorThenCloses() throws Exception {
        JwtUtil expiredTokenIssuer = new JwtUtil(jwtSecret, -1, 60_000, 60_000);
        String token = expiredTokenIssuer.generateAccessToken(1L, UserRole.USER, sessionId());

        assertConnectRejected("Bearer " + token, 401, 40100);
    }

    @Test
    void missingBearerPrefixReturnsStructuredErrorThenCloses() throws Exception {
        String token = jwtUtil.generateAccessToken(1L, UserRole.USER, sessionId());

        assertConnectRejected(token, 401, 41109);
    }

    @Test
    void missingAuthorizationHeaderReturnsStructuredErrorThenCloses() throws Exception {
        assertConnectRejected(null, 401, 41109);
    }

    @Test
    void revokedAccessTokenReturnsStructuredErrorThenCloses() throws Exception {
        Users user = createActiveUser();
        String sessionId = sessionId();
        String accessToken = jwtUtil.generateAccessToken(user.getUserId(), UserRole.USER, sessionId);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), UserRole.USER, sessionId);
        tokenSessionService.create(user.getUserId(), accessToken, refreshToken);
        tokenSessionService.revokeSession(user.getUserId(), sessionId);

        assertConnectRejected("Bearer " + accessToken, 401, 41103);
    }

    @Test
    void validAccessTokenConnectsAndNegotiatesTenSecondHeartbeat() throws Exception {
        Users user = createActiveUser();
        String sessionId = sessionId();
        String accessToken = jwtUtil.generateAccessToken(user.getUserId(), UserRole.USER, sessionId);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), UserRole.USER, sessionId);
        tokenSessionService.create(user.getUserId(), accessToken, refreshToken);

        Probe probe = open("Bearer " + accessToken, null);
        try {
            String connected = probe.awaitFrame("CONNECTED");
            assertThat(connected)
                    .contains("heart-beat:10000,10000")
                    .contains("user-name:" + user.getUserId());
            assertThat(probe.session().isOpen()).isTrue();
        } finally {
            probe.close();
        }
    }

    @Test
    void unauthorizedRoomSubscriptionReturnsStructuredErrorThenCloses() throws Exception {
        Users user = createActiveUser();
        String sessionId = sessionId();
        String accessToken = jwtUtil.generateAccessToken(user.getUserId(), UserRole.USER, sessionId);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), UserRole.USER, sessionId);
        tokenSessionService.create(user.getUserId(), accessToken, refreshToken);

        Probe probe = open("Bearer " + accessToken, subscribeFrame(9_999_999L));
        try {
            assertThat(probe.awaitFrame("CONNECTED")).startsWith("CONNECTED\n");
            ChatSocketErrorResponse error = parseError(probe.awaitFrame("ERROR"));
            assertThat(error.type()).isEqualTo("ERROR");
            assertThat(error.status()).isEqualTo(403);
            assertThat(error.code()).isEqualTo(48302);
            assertThat(error.operation()).isEqualTo("SUBSCRIBE");
            assertThat(error.roomId()).isEqualTo(9_999_999L);
            assertThat(probe.awaitClose()).isEqualTo(CloseStatus.PROTOCOL_ERROR);
        } finally {
            probe.close();
        }
    }

    private void assertConnectRejected(String authorization, int status, int code) throws Exception {
        Probe probe = open(authorization, null);
        try {
            ChatSocketErrorResponse error = parseError(probe.awaitFrame("ERROR"));
            assertThat(error.type()).isEqualTo("ERROR");
            assertThat(error.status()).isEqualTo(status);
            assertThat(error.code()).isEqualTo(code);
            assertThat(error.operation()).isEqualTo("CONNECT");
            assertThat(error.roomId()).isNull();
            assertThat(error.clientMessageId()).isNull();
            assertThat(probe.awaitClose()).isEqualTo(CloseStatus.PROTOCOL_ERROR);
        } finally {
            probe.close();
        }
    }

    private Probe open(String authorization, String afterConnectedFrame) throws Exception {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        BlockingQueue<CloseStatus> closes = new LinkedBlockingQueue<>();
        AtomicBoolean followUpSent = new AtomicBoolean();
        StandardWebSocketClient client = new StandardWebSocketClient();

        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                session.sendMessage(new TextMessage(connectFrame(authorization)));
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                String frame = message.getPayload().toString();
                frames.add(frame);
                if (afterConnectedFrame != null
                        && frame.startsWith("CONNECTED\n")
                        && followUpSent.compareAndSet(false, true)) {
                    session.sendMessage(new TextMessage(afterConnectedFrame));
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                // A server-side ERROR frame is asserted separately from transport callbacks.
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
                closes.add(closeStatus);
            }

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };

        WebSocketSession session = client.execute(
                handler, "ws://localhost:" + port + "/ws-stomp"
        ).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        return new Probe(session, frames, closes);
    }

    private String connectFrame(String authorization) {
        String authorizationHeader = authorization == null
                ? ""
                : "Authorization:" + authorization + "\n";
        return "CONNECT\n"
                + "accept-version:1.2\n"
                + "host:localhost\n"
                + "heart-beat:4000,4000\n"
                + authorizationHeader
                + "\n\0";
    }

    private String subscribeFrame(Long roomId) {
        return "SUBSCRIBE\n"
                + "id:unauthorized-room\n"
                + "destination:/sub/chat/room/" + roomId + "\n"
                + "\n\0";
    }

    private ChatSocketErrorResponse parseError(String frame) throws Exception {
        int bodyStart = frame.indexOf("\n\n");
        assertThat(bodyStart).isGreaterThanOrEqualTo(0);
        String body = frame.substring(bodyStart + 2).replace("\0", "");
        return objectMapper.readValue(body, ChatSocketErrorResponse.class);
    }

    private Users createActiveUser() {
        String suffix = UUID.randomUUID().toString();
        return userRepository.save(Users.builder()
                .username("stomp-connect-" + suffix)
                .passwordHash("password")
                .name("stomp-user")
                .status(UserStatus.ACTIVE)
                .build());
    }

    private String sessionId() {
        return UUID.randomUUID().toString();
    }

    private String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        char replacement = parts[2].charAt(0) == 'a' ? 'b' : 'a';
        parts[2] = replacement + parts[2].substring(1);
        return String.join(".", parts);
    }

    private record Probe(
            WebSocketSession session,
            BlockingQueue<String> frames,
            BlockingQueue<CloseStatus> closes
    ) {
        private String awaitFrame(String command) throws InterruptedException {
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                long remaining = deadline - System.nanoTime();
                String frame = frames.poll(Math.max(1, remaining), TimeUnit.NANOSECONDS);
                if (frame == null) break;
                if (frame.startsWith(command + "\n")) return frame;
            }
            throw new AssertionError("Timed out waiting for STOMP " + command + " frame");
        }

        private CloseStatus awaitClose() throws InterruptedException {
            return closes.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void close() throws Exception {
            if (session.isOpen()) session.close();
        }
    }
}
