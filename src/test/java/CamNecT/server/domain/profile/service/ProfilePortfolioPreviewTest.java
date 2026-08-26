package CamNecT.server.domain.profile.service;

import CamNecT.server.domain.portfolio.dto.response.PortfolioPreviewResponse;
import CamNecT.server.domain.portfolio.repository.PortfolioRepository;
import CamNecT.server.domain.profile.components.certificate.repository.CertificateRepository;
import CamNecT.server.domain.profile.components.education.repository.EducationRepository;
import CamNecT.server.domain.profile.components.experience.repository.ExperienceRepository;
import CamNecT.server.domain.profile.dto.response.ProfileResponse;
import CamNecT.server.domain.users.model.UserProfile;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserFollowRepository;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.users.repository.UserTagMapRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.point.service.PointService;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.service.PresignEngine;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import CamNecT.server.global.tag.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfilePortfolioPreviewTest {

    private static final String DEFAULT_THUMB_KEY =
            "camnect/portfolio/default/camnect_default_portfolio_thumbnail.png";
    private static final String DEFAULT_THUMB_URL = "https://cdn.camnect.site/" + DEFAULT_THUMB_KEY;

    private UserRepository userRepository;
    private UserProfileRepository userProfileRepository;
    private PortfolioRepository portfolioRepository;
    private PublicUrlIssuer publicUrlIssuer;
    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);
        portfolioRepository = mock(PortfolioRepository.class);
        publicUrlIssuer = mock(PublicUrlIssuer.class);

        profileService = new ProfileService(
                userRepository,
                mock(AccountAccessGuard.class),
                mock(CertificateRepository.class),
                mock(ExperienceRepository.class),
                userProfileRepository,
                mock(UserFollowRepository.class),
                portfolioRepository,
                mock(UserTagMapRepository.class),
                mock(EducationRepository.class),
                mock(TagRepository.class),
                mock(PresignEngine.class),
                publicUrlIssuer,
                mock(GlobalPresignMethods.class),
                mock(PointService.class)
        );
    }

    @Test
    void ownerProfileIncludesOwnerPortfolioPreviews() {
        stubProfileUser(1L);
        PortfolioPreviewResponse ownerOnly = preview(11L, "portfolio/1/private.png", false);
        when(portfolioRepository.findPreviewsByUserId(1L)).thenReturn(List.of(ownerOnly));
        when(publicUrlIssuer.issueImagePublicUrl(ownerOnly.thumbnailUrl())).thenReturn("owner-thumb");

        ProfileResponse response = profileService.getUserProfile(1L, 1L);

        assertThat(response.portfolioProjectList()).extracting(PortfolioPreviewResponse::portfolioId)
                .containsExactly(11L);
        verify(portfolioRepository).findPreviewsByUserId(1L);
        verify(portfolioRepository, never()).findPublicPreviewsByUserId(1L);
    }

    @Test
    void anotherUsersProfileIncludesOnlyPublicPortfolioPreviews() {
        stubProfileUser(2L);
        PortfolioPreviewResponse publicPreview = preview(22L, "portfolio/2/public.png", true);
        when(portfolioRepository.findPublicPreviewsByUserId(2L)).thenReturn(List.of(publicPreview));
        when(publicUrlIssuer.issueImagePublicUrl(publicPreview.thumbnailUrl())).thenReturn("public-thumb");

        ProfileResponse response = profileService.getUserProfile(1L, 2L);

        assertThat(response.portfolioProjectList()).extracting(PortfolioPreviewResponse::portfolioId)
                .containsExactly(22L);
        verify(portfolioRepository).findPublicPreviewsByUserId(2L);
        verify(portfolioRepository, never()).findPreviewsByUserId(2L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "기본이미지"})
    void canonicalizesMissingAndLegacyPortfolioThumbnails(String storedThumbnail) {
        stubProfileUser(1L);
        when(portfolioRepository.findPreviewsByUserId(1L))
                .thenReturn(List.of(preview(11L, storedThumbnail, true)));
        when(publicUrlIssuer.issueImagePublicUrl(DEFAULT_THUMB_KEY)).thenReturn(DEFAULT_THUMB_URL);

        ProfileResponse response = profileService.getUserProfile(1L, 1L);

        assertThat(response.portfolioProjectList()).singleElement()
                .extracting(PortfolioPreviewResponse::thumbnailUrl)
                .isEqualTo(DEFAULT_THUMB_URL);
        verify(publicUrlIssuer).issueImagePublicUrl(DEFAULT_THUMB_KEY);
    }

    private void stubProfileUser(Long userId) {
        Users user = Users.builder()
                .userId(userId)
                .name("user-" + userId)
                .status(UserStatus.ACTIVE)
                .build();
        UserProfile profile = UserProfile.builder()
                .user(user)
                .build();
        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    }

    private PortfolioPreviewResponse preview(Long id, String thumbnail, boolean isPublic) {
        return new PortfolioPreviewResponse(
                id,
                "portfolio-" + id,
                thumbnail,
                isPublic,
                false,
                LocalDate.of(2026, 8, 26)
        );
    }
}
