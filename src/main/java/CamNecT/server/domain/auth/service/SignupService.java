package CamNecT.server.domain.auth.service;

import CamNecT.server.domain.auth.dto.signup.VerifySignupEmailRequest;
import CamNecT.server.domain.users.model.UserProfile;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordService passwordService;

    @Transactional
    public Users signupVerifiedUser(VerifySignupEmailRequest req) {

        // 약관
        if (!req.agreements().serviceTerms() || !req.agreements().privacyTerms()) {
            throw new CustomException(AuthErrorCode.TERMS_REQUIRED);
        }

        // 비밀번호 정책
        passwordService.validatePassword(req.password());

        // 최종 유니크
        if (userRepository.existsByEmail(req.email())) throw new CustomException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        if (userRepository.existsByUsername(req.username())) throw new CustomException(AuthErrorCode.USERNAME_ALREADY_EXISTS);
        if (userRepository.existsByPhoneNum(req.phoneNum())) throw new CustomException(AuthErrorCode.PHONENUM_ALREADY_EXISTS);

        Users user = Users.builder()
                .email(req.email())
                .username(req.username())
                .name(req.name())
                .phoneNum(req.phoneNum())
                .passwordHash(passwordEncoder.encode(req.password()))
                .status(UserStatus.ADMIN_PENDING)
                .build();
        user.ensureSuspensionRecord();

        Users savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(AuthErrorCode.DUPLICATE_RESOURCE);
        }

        UserProfile emptyProfile = UserProfile.builder()
                .user(savedUser)
                .bio(null)
                .profileImageKey(null)
                .openToCoffeeChat(true)
                .studentNo(null)
                .yearLevel(null)
                .institutionId(null)
                .majorId(null)
                .build();

        userProfileRepository.save(emptyProfile);

        return savedUser;
    }
}
