package CamNecT.server.domain.auth.controller;

import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.verification.email.model.EmailVerificationToken;
import CamNecT.server.domain.verification.email.repository.EmailVerificationTokenRepository;
import CamNecT.server.global.jwt.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordResetIntegrationTest {

    private static final String VALID_CODE = "123456";
    private static final String WRONG_CODE = "000000";
    private static final String CURRENT_PASSWORD = "oldpass1";
    private static final String NEW_PASSWORD = "newpass2";
    private static final String REPLAY_PASSWORD = "thirdpass3";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired EmailVerificationTokenRepository tokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    @Test
    void verifiedResetTokenChangesPasswordOnceAndRejectsReplay() throws Exception {
        Users user = createRecoverableUser();
        tokenRepository.save(EmailVerificationToken.issueForEmail(user.getEmail(), VALID_CODE, 30));

        String verifyBody = mockMvc.perform(post("/api/auth/password/reset/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "code", VALID_CODE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode verifyResponse = objectMapper.readTree(verifyBody);
        String resetToken = verifyResponse.get("resetToken").asText();

        mockMvc.perform(patch("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("resetToken", resetToken, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Users changed = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, changed.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(CURRENT_PASSWORD, changed.getPasswordHash())).isFalse();

        reset(resetToken, REPLAY_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(41103));

        Users afterReplay = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, afterReplay.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(REPLAY_PASSWORD, afterReplay.getPasswordHash())).isFalse();
    }

    @Test
    void fifthWrongCodeLocksVerificationAndPersistsAttemptCount() throws Exception {
        Users user = createRecoverableUser();
        tokenRepository.saveAndFlush(EmailVerificationToken.issueForEmail(user.getEmail(), VALID_CODE, 30));

        for (int attempt = 1; attempt <= 4; attempt++) {
            verifyWrongCode(user.getEmail())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(42032));

            assertThat(currentToken(user.getEmail()).getAttemptCount()).isEqualTo(attempt);
        }

        verifyWrongCode(user.getEmail())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42920));

        assertThat(currentToken(user.getEmail()).getAttemptCount()).isEqualTo(5);

        mockMvc.perform(post("/api/auth/password/reset/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "code", VALID_CODE))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42920));
    }

    @Test
    void resetPasswordReturnsDocumentedValidationErrors() throws Exception {
        Users user = createRecoverableUser();
        String resetToken = jwtUtil.generatePasswordResetToken(
                user.getUserId(), user.getRole(), user.getPasswordHash());

        reset(resetToken, "short1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(41010));

        reset(resetToken, CURRENT_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(41011));

        mockMvc.perform(patch("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("resetToken", resetToken))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void resetPasswordRejectsInvalidOrWrongTypeToken() throws Exception {
        Users user = createRecoverableUser();
        String accessToken = jwtUtil.generateAccessToken(
                user.getUserId(),
                user.getRole(),
                UUID.randomUUID().toString()
        );

        reset(accessToken, NEW_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(41106));

        reset("not-a-jwt", NEW_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void resetPasswordReturnsNotFoundWhenTokenUserDoesNotExist() throws Exception {
        String resetToken = jwtUtil.generatePasswordResetToken(
                Long.MAX_VALUE, UserRole.USER, "missing-user-hash");

        reset(resetToken, NEW_PASSWORD)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(41401));
    }

    @Test
    void resetPasswordRechecksAccountStatusAfterTokenWasIssued() throws Exception {
        Users suspended = createRecoverableUser();
        String suspendedToken = jwtUtil.generatePasswordResetToken(
                suspended.getUserId(), suspended.getRole(), suspended.getPasswordHash());
        suspended.changeStatus(UserStatus.SUSPENDED);
        userRepository.saveAndFlush(suspended);

        reset(suspendedToken, NEW_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(41302));

        Users withdrawn = createRecoverableUser();
        String withdrawnToken = jwtUtil.generatePasswordResetToken(
                withdrawn.getUserId(), withdrawn.getRole(), withdrawn.getPasswordHash());
        withdrawn.changeStatus(UserStatus.WITHDRAWN);
        userRepository.saveAndFlush(withdrawn);

        reset(withdrawnToken, NEW_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(41303));
    }

    private org.springframework.test.web.servlet.ResultActions verifyWrongCode(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password/reset/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "code", WRONG_CODE))));
    }

    private org.springframework.test.web.servlet.ResultActions reset(String resetToken, String newPassword) throws Exception {
        return mockMvc.perform(patch("/api/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("resetToken", resetToken, "newPassword", newPassword))));
    }

    private EmailVerificationToken currentToken(String email) {
        return tokenRepository.findTopByEmailAndUsedAtIsNullOrderByIdDesc(email).orElseThrow();
    }

    private Users createRecoverableUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.saveAndFlush(Users.builder()
                .username("reset-" + suffix)
                .passwordHash(passwordEncoder.encode(CURRENT_PASSWORD))
                .name("reset user")
                .email("reset-" + suffix + "@example.com")
                .status(UserStatus.ACTIVE)
                .build());
    }

    private byte[] json(Object value) throws Exception {
        return objectMapper.writeValueAsBytes(value);
    }
}
