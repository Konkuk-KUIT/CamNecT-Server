package CamNecT.server.domain.profile.service;

import CamNecT.server.global.point.service.PointService;
import CamNecT.server.domain.profile.components.certificate.dto.response.CertificateResponse;
import CamNecT.server.domain.profile.components.certificate.repository.CertificateRepository;
import CamNecT.server.domain.profile.components.education.dto.response.EducationResponse;
import CamNecT.server.domain.profile.components.education.repository.EducationRepository;
import CamNecT.server.domain.profile.components.experience.dto.response.ExperienceResponse;
import CamNecT.server.domain.profile.components.experience.repository.ExperienceRepository;
import CamNecT.server.domain.chat.model.ChatRequest;
import CamNecT.server.domain.chat.model.ChatRoom;
import CamNecT.server.domain.chat.repository.ChatRoomRepository;
import CamNecT.server.domain.portfolio.dto.response.PortfolioPreviewResponse;
import CamNecT.server.domain.portfolio.repository.PortfolioRepository;
import CamNecT.server.domain.profile.dto.request.UpdateOnboardingRequest;
import CamNecT.server.domain.profile.dto.request.UpdatePrivacyRequest;
import CamNecT.server.domain.profile.dto.request.UpdateProfileImageRequest;
import CamNecT.server.domain.profile.dto.request.UpdateProfileTagsRequest;
import CamNecT.server.domain.profile.dto.response.ProfileSettingsResponse;
import CamNecT.server.domain.profile.dto.response.ProfileStatusResponse;
import CamNecT.server.domain.profile.dto.response.ProfileResponse;
import CamNecT.server.domain.profile.dto.response.ProfileTagDto;
import CamNecT.server.domain.users.model.UserProfile;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.UserTagMap;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserFollowRepository;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.users.repository.UserTagMapRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.StorageErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.dto.request.PresignUploadRequest;
import CamNecT.server.global.storage.dto.response.PresignUploadResponse;
import CamNecT.server.global.storage.model.UploadPurpose;
import CamNecT.server.global.storage.model.UploadRefType;
import CamNecT.server.global.storage.service.PresignEngine;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import CamNecT.server.global.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    @Value("${app.profile.image.max-file-size-mb:20}")
    private int profileImageMaxFileSizeMb;

    private static final Set<String> PROFILE_IMAGE_ALLOWED =
            Set.of("image/jpeg", "image/png", "image/webp");
    
    private static final String DEFAULT_PORTFOLIO_THUMB_KEY =
            "camnect/portfolio/default/camnect_default_portfolio_thumbnail.png";

    private final UserRepository userRepository;
    private final CertificateRepository certificateRepository;
    private final ExperienceRepository experienceRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserFollowRepository userFollowRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final PortfolioRepository portfolioRepository;
    private final UserTagMapRepository userTagMapRepository;
    private final EducationRepository educationRepository;
    private final TagRepository tagRepository;
    private final PresignEngine presignEngine;
    private final PublicUrlIssuer publicUrlIssuer;
    private final GlobalPresignMethods globalPresignMethods;
    private final PointService pointService;

    @Transactional(readOnly = true)
    public ProfileResponse getUserProfile(Long loginUserId, Long profileUserId) {

        Users user = userRepository.findByUserId(profileUserId)
                .orElseThrow(() -> new CustomException(
                        Objects.equals(loginUserId, profileUserId)
                                ? AuthErrorCode.INVALID_TOKEN
                                : UserErrorCode.USER_NOT_FOUND
                ));
        UserProfile userProfile = userProfileRepository.findByUserId(profileUserId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_PROFILE_NOT_FOUND));

        String profileImageUrl = publicUrlIssuer.issuePublicUrl(userProfile.getProfileImageKey());

        boolean isOwner = (loginUserId != null) && loginUserId.equals(profileUserId);

        boolean isFollowing = (loginUserId != null && !isOwner)
                && userFollowRepository.existsByFollowerIdAndFollowingId(loginUserId, profileUserId);

        boolean showFollower = isOwner || Boolean.TRUE.equals(userProfile.getIsFollowerVisible());
        boolean showEducation = isOwner || Boolean.TRUE.equals(userProfile.getIsEducationVisible());
        boolean showExperience = isOwner || Boolean.TRUE.equals(userProfile.getIsExperienceVisible());
        boolean showCertificate = isOwner || Boolean.TRUE.equals(userProfile.getIsCertificateVisible());

        int following = showFollower ? userFollowRepository.countByFollowerId(profileUserId) : 0;
        int follower = showFollower ? userFollowRepository.countByFollowingId(profileUserId) : 0;
        int myPoints = isOwner ? pointService.getBalance(profileUserId) : 0;
        boolean hasChat = !isOwner && chatRoomRepository.findActiveChatPartnerIds(
                loginUserId,
                List.of(profileUserId),
                ChatRoom.RoomStatus.OPEN,
                ChatRequest.RequestStatus.ACCEPTED,
                ChatRequest.RequestType.COFFEE_CHAT
        ).contains(profileUserId);

        List<PortfolioPreviewResponse> portfolioPreviewResponses =
                portfolioRepository.findPreviewsByUserId(profileUserId).stream()
                        .map(this::toCdnPreview)
                        .toList();

        List<EducationResponse> educationResponses = showEducation
                ? educationRepository.findAllByUserIdWithDetails(profileUserId)
                .stream().map(EducationResponse::from).toList()
                : Collections.emptyList();

        List<ExperienceResponse> experienceList = showExperience
                ? experienceRepository.findAllByUserIdWithDetails(profileUserId)
                .stream().map(ExperienceResponse::from).toList()
                : Collections.emptyList();

        List<CertificateResponse> certificateList = showCertificate
                ? certificateRepository.findAllByUser_UserIdOrderByAcquiredDateDesc(profileUserId)
                .stream().map(CertificateResponse::from).toList()
                : Collections.emptyList();

        List<ProfileTagDto> tags = userTagMapRepository.findAllTagsByUserId(profileUserId).stream()
                .map(t -> new ProfileTagDto(t.getId(), t.getName(), t.getCategory().getCode()))
                .toList();

        ProfileResponse.ProfileBasicsDto basicProfile = new ProfileResponse.ProfileBasicsDto(
                userProfile.getBio(),
                userProfile.getOpenToCoffeeChat(),
                userProfile.getIsFollowerVisible(),
                userProfile.getIsEducationVisible(),
                userProfile.getIsExperienceVisible(),
                userProfile.getIsCertificateVisible(),
                profileImageUrl,
                userProfile.getStudentNo(),
//                userProfile.getYearLevel(),
                userProfile.getInstitutionId(),
                userProfile.getMajorId()
        );

        return new ProfileResponse(
                user.getUserId(),
                user.getName(),
                basicProfile,
                isFollowing,
                following,
                follower,
                myPoints,
                hasChat,
                portfolioPreviewResponses,
                educationResponses,
                experienceList,
                certificateList,
                tags
        );
    }

    @Transactional
    public void updatePrivacy(Long userId, UpdatePrivacyRequest request) {
        userRepository.lockUserRow(userId);

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_PROFILE_NOT_FOUND));

        profile.updatePrivacySettings(
                request.isFollowerVisible(),
                request.isEducationVisible(),
                request.isExperienceVisible(),
                request.isCertificateVisible()
        );
    }

    @Transactional
    public ProfileStatusResponse updateBio(Long userId, String bio) {
        userRepository.lockUserRow(userId);

        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_PROFILE_NOT_FOUND));

        userProfile.updateBio(bio);

        return new ProfileStatusResponse(userProfile.getUser().getStatus());
    }

    @Transactional
    public ProfileStatusResponse createOnboarding(Long userId, UpdateOnboardingRequest req) {

        userRepository.lockUserRow(userId);
        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));

        requireAccessible(user);
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_PROFILE_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE || userProfile.isInitialSetupCompleted()) {
            throw new CustomException(AuthErrorCode.INITIAL_SETUP_NOT_ALLOWED);
        }

        // 1) bio 정리
        String bio = trimToNull(req.bio());

        // 2) tagIds 정리 + 검증(활성 태그만)
        List<Long> tagIds = (req.tagIds() == null) ? List.of() : req.tagIds().stream().distinct().toList();

        if (!tagIds.isEmpty()) {
            List<Long> exist = tagRepository.findExistingActiveIds(tagIds);
            if (exist.size() != tagIds.size()) {
                throw new CustomException(UserErrorCode.INVALID_TAG_IDS);
            }
        }

        // 4) 태그 replace (온보딩에서 비어도 OK)
        userTagMapRepository.deleteAllByUserId(userId);

        if (!tagIds.isEmpty()) {
            userTagMapRepository.saveAll(
                    tagIds.stream()
                            .map(tid -> UserTagMap.builder().userId(userId).tagId(tid).build())
                            .toList()
            );
        }

        // 5) 마지막에 consume + 프로필 업데이트 (고아 파일 최소화)
        String finalProfileImageKey = null;
        if (StringUtils.hasText(req.profileImageKey())) {
            String finalPrefix = "profile/user-" + userId + "/images";
            finalProfileImageKey = presignEngine.consume(
                    userId,
                    UploadPurpose.PROFILE_IMAGE,
                    UploadRefType.USER_PROFILE,
                    userId,
                    req.profileImageKey(),
                    finalPrefix
            );
        }

        userProfile.updateOnboardingProfile(bio, finalProfileImageKey);
        userProfile.completeInitialSetup();

        return new ProfileStatusResponse(user.getStatus());
    }


    // =========================================================
    // 분야별 태그(관심분야 태그) 선택
    // =========================================================
    @Transactional
    public ProfileStatusResponse updateProfileTags(Long userId, UpdateProfileTagsRequest req) {

        userRepository.lockUserRow(userId);
        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));

        requireAccessible(user);

        List<Long> tagIds = (req.tagIds() == null) ? List.of() : req.tagIds().stream().distinct().toList();

        // 존재 검증 (스킵 허용이면 empty OK)
        if (!tagIds.isEmpty()) {
            List<Long> existingActiveTagIds = tagRepository.findExistingActiveIds(tagIds);
            if (existingActiveTagIds.size() != tagIds.size()) {
                throw new CustomException(UserErrorCode.INVALID_TAG_IDS);
            }
        }

        // 프로필 노출 태그 저장(user_tag_map)
        userTagMapRepository.deleteAllByUserId(userId);
        userTagMapRepository.saveAll(
                tagIds.stream()
                        .map(tid -> UserTagMap.builder().userId(userId).tagId(tid).build())
                        .toList()
        );
        return new ProfileStatusResponse(user.getStatus());
    }

    @Transactional
    public PresignUploadResponse presignProfileImageUpload(Long userId, PresignUploadRequest req) {
        userRepository.lockUserRow(userId);
        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        requireAccessible(user);

        String ct = globalPresignMethods.normalize(req.contentType());

        long maxBytes = (long) profileImageMaxFileSizeMb * 1024 * 1024;

        if (!PROFILE_IMAGE_ALLOWED.contains(ct)) throw new CustomException(StorageErrorCode.UNSUPPORTED_CONTENT_TYPE); // 프로젝트 에러코드에 맞춰 변경

        if (req.size() == null || req.size() <= 0) throw new CustomException(StorageErrorCode.STORAGE_EMPTY_FILE);
        if (req.size() > maxBytes) throw new CustomException(StorageErrorCode.FILE_TOO_LARGE);

        String keyPrefix = "profile/user-" + userId + "/images";
        return presignEngine.issueUpload(
                userId,
                UploadPurpose.PROFILE_IMAGE,
                keyPrefix,
                ct,
                req.size(),
                req.originalFilename()
        );
    }

    @Transactional
    public void updateMyProfileImage(Long userId, UpdateProfileImageRequest req) {

        userRepository.lockUserRow(userId);
        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        requireAccessible(user);

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_PROFILE_NOT_FOUND));

        String oldKey = profile.getProfileImageKey();

        String newFinalKey = null;
        if (StringUtils.hasText(req.profileImageKey())) {
            String finalPrefix = "profile/user-" + userId + "/images";
            newFinalKey = presignEngine.consume(
                    userId,
                    UploadPurpose.PROFILE_IMAGE,
                    UploadRefType.USER_PROFILE,
                    userId,
                    req.profileImageKey(),
                    finalPrefix
            );
        }

        profile.updateProfileImageKey(newFinalKey); // setter/메서드로 반영

        // 기존 이미지 정리(새 키와 다를 때만)
        if (StringUtils.hasText(oldKey) && !Objects.equals(oldKey, newFinalKey)) {
            globalPresignMethods.deleteAfterCommit(Set.of(oldKey));
        }
    }

    private void requireAccessible(Users user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(AuthErrorCode.USER_WITHDRAWN);
        }
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }


    public ProfileSettingsResponse getMySettings(Long userId) {
        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_PROFILE_NOT_FOUND));

        String profileImageUrl = publicUrlIssuer.issuePublicUrl(userProfile.getProfileImageKey());

        return new ProfileSettingsResponse(
                user.getUserId(),
                user.getName(),
                profileImageUrl,
                user.getPhoneNum(),
                user.getEmail()
        );
    }

    private PortfolioPreviewResponse toCdnPreview(PortfolioPreviewResponse p) {
        return new PortfolioPreviewResponse(
                p.portfolioId(),
                p.title(),
                p.subtitle(),
                portfolioThumbOrDefault(p.thumbnailUrl()),
                p.isPublic(),
                p.isFavorite(),
                p.updatedAt()
        );
    }

    private String portfolioThumbOrDefault(String key) {
        String safeKey = StringUtils.hasText(key) ? key : DEFAULT_PORTFOLIO_THUMB_KEY;

        try {
            String url = publicUrlIssuer.issueImagePublicUrl(safeKey);
            return StringUtils.hasText(url) ? url : ("https://cdn.camnect.site/" + DEFAULT_PORTFOLIO_THUMB_KEY);
        } catch (Exception e) {
            log.warn("issueImagePublicUrl failed. key={}", safeKey, e);
            return "https://cdn.camnect.site/" + DEFAULT_PORTFOLIO_THUMB_KEY;
        }
    }
}
