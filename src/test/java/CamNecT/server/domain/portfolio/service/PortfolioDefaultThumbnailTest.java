package CamNecT.server.domain.portfolio.service;

import CamNecT.server.domain.community.service.AuthorAssembler;
import CamNecT.server.domain.portfolio.dto.response.PortfolioPreviewResponse;
import CamNecT.server.domain.portfolio.dto.response.PortfolioResponse;
import CamNecT.server.domain.portfolio.repository.PortfolioAssetRepository;
import CamNecT.server.domain.portfolio.repository.PortfolioRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.PresignEngine;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioDefaultThumbnailTest {

    private static final String DEFAULT_THUMB_KEY =
            "camnect/portfolio/default/camnect_default_portfolio_thumbnail.png";
    private static final String DEFAULT_THUMB_URL = "https://cdn.camnect.site/" + DEFAULT_THUMB_KEY;

    private UserRepository userRepository;
    private PortfolioRepository portfolioRepository;
    private PublicUrlIssuer publicUrlIssuer;
    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        portfolioRepository = mock(PortfolioRepository.class);
        publicUrlIssuer = mock(PublicUrlIssuer.class);
        portfolioService = new PortfolioService(
                userRepository,
                mock(AccountAccessGuard.class),
                portfolioRepository,
                mock(PortfolioAssetRepository.class),
                mock(UploadTicketRepository.class),
                mock(AuthorAssembler.class),
                mock(PresignEngine.class),
                publicUrlIssuer,
                mock(PortfolioAttachmentService.class)
        );

        Users user = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsById(1L)).thenReturn(true);
        when(publicUrlIssuer.issuePublicUrl(DEFAULT_THUMB_KEY)).thenReturn(DEFAULT_THUMB_URL);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "기본이미지"})
    void canonicalizesMissingAndLegacyPortfolioThumbnails(String storedThumbnail) {
        when(portfolioRepository.findPreviewsByUserId(1L)).thenReturn(List.of(
                new PortfolioPreviewResponse(
                        11L,
                        "portfolio",
                        storedThumbnail,
                        true,
                        false,
                        LocalDate.of(2026, 8, 26)
                )
        ));

        PortfolioResponse<List<PortfolioPreviewResponse>> response = portfolioService.portfolioPreview(1L, 1L);

        assertThat(response.data()).singleElement()
                .extracting(PortfolioPreviewResponse::thumbnailUrl)
                .isEqualTo(DEFAULT_THUMB_URL);
        verify(publicUrlIssuer).issuePublicUrl(DEFAULT_THUMB_KEY);
    }
}
