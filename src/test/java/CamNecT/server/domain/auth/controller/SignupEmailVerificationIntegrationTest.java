package CamNecT.server.domain.auth.controller;

import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.verification.email.model.EmailVerificationToken;
import CamNecT.server.domain.verification.email.repository.EmailVerificationTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SignupEmailVerificationIntegrationTest {

    private static final String VALID_CODE = "123456";
    private static final String WRONG_CODE = "000000";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired UserProfileRepository userProfileRepository;
    @Autowired EmailVerificationTokenRepository tokenRepository;

    @Test
    void unverifiedSignupExistsOnlyAsEmailTokenAndCannotLogin() throws Exception {
        String suffix = suffix();
        String email = "pending-" + suffix + "@example.com";
        String username = "pending-" + suffix;
        tokenRepository.saveAndFlush(EmailVerificationToken.issueForEmail(email, VALID_CODE, 30));

        assertThat(userRepository.findByEmail(email)).isEmpty();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "password1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(41101));

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void wrongSignupCodesPersistAttemptsWithoutCreatingUser() throws Exception {
        String suffix = suffix();
        String email = "attempt-" + suffix + "@example.com";
        EmailVerificationToken issued = tokenRepository.saveAndFlush(
                EmailVerificationToken.issueForEmail(email, VALID_CODE, 30)
        );

        for (int attempt = 1; attempt <= 4; attempt++) {
            verifySignup(email, "attempt-" + suffix, WRONG_CODE)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(42032));

            assertThat(tokenRepository.findById(issued.getId()).orElseThrow().getAttemptCount())
                    .isEqualTo(attempt);
            assertThat(userRepository.findByEmail(email)).isEmpty();
        }

        verifySignup(email, "attempt-" + suffix, WRONG_CODE)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42920));

        assertThat(tokenRepository.findById(issued.getId()).orElseThrow().getAttemptCount()).isEqualTo(5);
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void validCodeAtomicallyCreatesAdminPendingUserAndIncompleteProfile() throws Exception {
        String suffix = suffix();
        String email = "verified-" + suffix + "@example.com";
        String username = "verified-" + suffix;
        EmailVerificationToken issued = tokenRepository.saveAndFlush(
                EmailVerificationToken.issueForEmail(email, VALID_CODE, 30)
        );

        verifySignup(email, username, VALID_CODE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyVerified").value(false))
                .andExpect(jsonPath("$.tempToken").isString());

        Users user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ADMIN_PENDING);
        assertThat(userProfileRepository.findByUserId(user.getUserId()).orElseThrow().isInitialSetupCompleted())
                .isFalse();
        assertThat(tokenRepository.findById(issued.getId()).orElseThrow().getUsedAt()).isNotNull();
    }

    @Test
    void duplicatePhoneReturnsConflictWithoutUnexpectedRollback() throws Exception {
        String firstSuffix = suffix();
        String duplicatePhone = "010" + suffix();
        String firstEmail = "phone-first-" + firstSuffix + "@example.com";
        tokenRepository.saveAndFlush(EmailVerificationToken.issueForEmail(firstEmail, VALID_CODE, 30));

        verifySignup(firstEmail, "phone-first-" + firstSuffix, VALID_CODE, duplicatePhone)
                .andExpect(status().isOk());

        String secondSuffix = suffix();
        String secondEmail = "phone-second-" + secondSuffix + "@example.com";
        tokenRepository.saveAndFlush(EmailVerificationToken.issueForEmail(secondEmail, VALID_CODE, 30));

        verifySignup(secondEmail, "phone-second-" + secondSuffix, VALID_CODE, duplicatePhone)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(41903));

        assertThat(userRepository.findByEmail(secondEmail)).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions verifySignup(
            String email,
            String username,
            String code
    ) throws Exception {
        return verifySignup(email, username, code, "010" + suffix());
    }

    private org.springframework.test.web.servlet.ResultActions verifySignup(
            String email,
            String username,
            String code,
            String phoneNum
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/signup/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "code", code,
                        "username", username,
                        "password", "password1",
                        "name", "signup user",
                        "phoneNum", phoneNum,
                        "agreements", Map.of(
                                "serviceTerms", true,
                                "privacyTerms", true
                        )
                ))));
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private byte[] json(Object value) throws Exception {
        return objectMapper.writeValueAsBytes(value);
    }
}
