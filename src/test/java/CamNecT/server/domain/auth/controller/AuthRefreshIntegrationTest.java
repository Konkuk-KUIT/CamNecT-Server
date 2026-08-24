package CamNecT.server.domain.auth.controller;

import CamNecT.server.domain.users.model.UserProfile;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.jwt.model.TokenType;
import CamNecT.server.global.jwt.service.TokenSessionService;
import CamNecT.server.global.jwt.util.JwtUtil;
import CamNecT.server.global.notification.model.Platform;
import CamNecT.server.global.notification.model.PushDevice;
import CamNecT.server.global.notification.repository.PushDeviceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class AuthRefreshIntegrationTest {

    private static final String TEST_JWT_SECRET =
            "test-jwt-secret-key-for-context-loads-at-least-32-characters";
    private static final String PASSWORD = "password1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired UserProfileRepository userProfileRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TokenSessionService tokenSessionService;
    @Autowired JwtUtil jwtUtil;
    @Autowired PushDeviceRepository pushDeviceRepository;

    @Test
    void rotatesBothTokensAndRequiresTheNewRefreshToken() throws Exception {
        Users user = createActiveUser();
        LoginTokens login = login(user);
        LoginTokens otherDevice = login(user);
        String sessionId = jwtUtil.getSessionId(login.refreshToken());

        assertThat(jwtUtil.getSessionId(otherDevice.refreshToken())).isNotEqualTo(sessionId);

        String responseBody = refresh(login.refreshToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.accessTokenExpiresInMs").value(172_800_000L))
                .andExpect(jsonPath("$.data.refreshTokenExpiresInMs").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(responseBody).get("data");
        String newAccessToken = data.get("accessToken").asText();
        String newRefreshToken = data.get("refreshToken").asText();

        assertThat(newAccessToken).isNotEqualTo(login.accessToken());
        assertThat(newRefreshToken).isNotEqualTo(login.refreshToken());
        assertThat(jwtUtil.getSessionId(newAccessToken)).isEqualTo(sessionId);
        assertThat(jwtUtil.getSessionId(newRefreshToken)).isEqualTo(sessionId);
        assertDoesNotThrow(() -> tokenSessionService.requireActiveAccess(login.userId(), newAccessToken));
        assertDoesNotThrow(() -> tokenSessionService.requireActiveAccess(otherDevice.userId(), otherDevice.accessToken()));

        refresh(login.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(41107));

        assertThrows(
                CustomException.class,
                () -> tokenSessionService.requireActiveAccess(login.userId(), newAccessToken)
        );
        assertDoesNotThrow(() -> tokenSessionService.requireActiveAccess(otherDevice.userId(), otherDevice.accessToken()));
    }

    @Test
    void logoutRevokesOnlyTheCurrentLoginSessionAndPushDevice() throws Exception {
        Users user = createActiveUser();
        LoginTokens currentDevice = login(user);
        LoginTokens otherDevice = login(user);
        savePushDevice(user.getUserId(), "current-device", "current-token");
        savePushDevice(user.getUserId(), "other-device", "other-token");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + currentDevice.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("deviceId", "current-device"))))
                .andExpect(status().isOk());

        assertThrows(
                CustomException.class,
                () -> tokenSessionService.requireActiveAccess(user.getUserId(), currentDevice.accessToken())
        );
        assertDoesNotThrow(
                () -> tokenSessionService.requireActiveAccess(user.getUserId(), otherDevice.accessToken())
        );
        assertThat(pushDeviceRepository.findByUserIdAndDeviceId(user.getUserId(), "current-device"))
                .get()
                .extracting(PushDevice::isEnabled)
                .isEqualTo(false);
        assertThat(pushDeviceRepository.findByUserIdAndDeviceId(user.getUserId(), "other-device"))
                .get()
                .extracting(PushDevice::isEnabled)
                .isEqualTo(true);
    }

    @Test
    void bodylessLegacyLogoutKeepsAuthSessionsDeviceScopedButDisablesAllPushDevices() throws Exception {
        Users user = createActiveUser();
        LoginTokens currentDevice = login(user);
        LoginTokens otherDevice = login(user);
        savePushDevice(user.getUserId(), "current-device", "current-token");
        savePushDevice(user.getUserId(), "other-device", "other-token");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + currentDevice.accessToken()))
                .andExpect(status().isOk());

        assertThrows(
                CustomException.class,
                () -> tokenSessionService.requireActiveAccess(user.getUserId(), currentDevice.accessToken())
        );
        assertDoesNotThrow(
                () -> tokenSessionService.requireActiveAccess(user.getUserId(), otherDevice.accessToken())
        );
        assertThat(pushDeviceRepository.findAllByUserIdAndEnabledTrue(user.getUserId())).isEmpty();
    }

    @Test
    void rejectsBlankLogoutDeviceIdWithoutRevokingTheSession() throws Exception {
        Users user = createActiveUser();
        LoginTokens login = login(user);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("deviceId", "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        assertDoesNotThrow(
                () -> tokenSessionService.requireActiveAccess(user.getUserId(), login.accessToken())
        );
    }

    @Test
    void rejectsMissingMalformedAndWrongTypeTokensWithStableCodes() throws Exception {
        LoginTokens login = login(createActiveUser());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        refresh("not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(41103));

        refresh(login.accessToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(41106));
    }

    @Test
    void returnsRefreshSpecificCodeForExpiredToken() throws Exception {
        Users user = createActiveUser();
        JwtUtil expiredTokenIssuer = new JwtUtil(TEST_JWT_SECRET, 60_000L, -1L, 60_000L);
        String expiredRefreshToken = expiredTokenIssuer.generateRefreshToken(
                user.getUserId(),
                UserRole.USER,
                UUID.randomUUID().toString()
        );

        refresh(expiredRefreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(41108));
    }

    @Test
    void rejectsPreviouslyIssuedTokensWithoutSessionId() throws Exception {
        Users user = createActiveUser();
        String legacyAccessToken = legacyToken(user, TokenType.ACCESS);
        String legacyRefreshToken = legacyToken(user, TokenType.REFRESH);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + legacyAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(41103));

        refresh(legacyRefreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(41103));
    }

    private LoginTokens login(Users user) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", user.getUsername(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        return new LoginTokens(
                user.getUserId(),
                response.get("accessToken").asText(),
                response.get("refreshToken").asText()
        );
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refreshToken))));
    }

    private Users createActiveUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Users user = userRepository.saveAndFlush(Users.builder()
                .username("refresh-" + suffix)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .name("refresh user")
                .email("refresh-" + suffix + "@example.com")
                .status(UserStatus.ACTIVE)
                .build());
        userProfileRepository.saveAndFlush(UserProfile.builder().user(user).build());
        return user;
    }

    private void savePushDevice(Long userId, String deviceId, String fcmToken) {
        pushDeviceRepository.saveAndFlush(PushDevice.builder()
                .userId(userId)
                .deviceId(deviceId)
                .platform(Platform.WEB)
                .fcmToken(fcmToken)
                .enabled(true)
                .build());
    }

    private byte[] json(Object value) throws Exception {
        return objectMapper.writeValueAsBytes(value);
    }

    private String legacyToken(Users user, TokenType tokenType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().type("JWT").and()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getUserId()))
                .claim("type", tokenType.name())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private record LoginTokens(Long userId, String accessToken, String refreshToken) {}
}
