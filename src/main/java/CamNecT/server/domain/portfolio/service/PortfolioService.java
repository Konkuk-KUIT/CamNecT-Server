package CamNecT.server.domain.portfolio.service;

import CamNecT.server.domain.community.dto.AuthorDto;
import CamNecT.server.domain.community.service.AuthorAssembler;
import CamNecT.server.domain.portfolio.dto.PortfolioProjectDto;
import CamNecT.server.domain.portfolio.dto.request.PortfolioRequest;
import CamNecT.server.domain.portfolio.dto.response.PortfolioAssetView;
import CamNecT.server.domain.portfolio.dto.response.PortfolioDetailResponse;
import CamNecT.server.domain.portfolio.dto.response.PortfolioPreviewResponse;
import CamNecT.server.domain.portfolio.dto.response.PortfolioResponse;
import CamNecT.server.domain.portfolio.model.PortfolioAsset;
import CamNecT.server.domain.portfolio.model.PortfolioProject;
import CamNecT.server.domain.portfolio.repository.PortfolioAssetRepository;
import CamNecT.server.domain.portfolio.repository.PortfolioRepository;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import CamNecT.server.global.storage.model.UploadTicket;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.PresignEngine;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {
    private static final String DEFAULT_THUMB = "기본이미지";
    private static final String DEFAULT_THUMB_KEY =
            "camnect/portfolio/default/camnect_default_portfolio_thumbnail.png";

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final UploadTicketRepository uploadTicketRepository;
    private final AuthorAssembler authorAssembler;

    private final PresignEngine presignEngine;
    private final PublicUrlIssuer publicUrlIssuer;
    private final PortfolioAttachmentService portfolioAttachmentService;

    public PortfolioResponse<List<PortfolioPreviewResponse>> portfolioPreview(Long userId, Long portfolioUserId) {
        requireAuthenticatedUser(userId);
        if (!userRepository.existsById(portfolioUserId)) {
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        }

        boolean isMine = Objects.equals(userId, portfolioUserId);

        List<PortfolioPreviewResponse> rows = isMine
                ? portfolioRepository.findPreviewsByUserId(portfolioUserId)
                : portfolioRepository.findPublicPreviewsByUserId(portfolioUserId);

        List<PortfolioPreviewResponse> resultList =  rows.stream()
                .map(r -> new PortfolioPreviewResponse(
                        r.portfolioId(),
                        r.title(),
                        cdnOrDefault(r.thumbnailUrl()),
                        r.isPublic(),
                        r.isFavorite(),
                        r.updatedAt()
                ))
                .toList();

        return PortfolioResponse.of(isMine, resultList);
    }

    public PortfolioResponse<PortfolioDetailResponse> portfolioDetail(Long userId, Long portfolioUserId, Long portfolioId) {
        requireAuthenticatedUser(userId);
        PortfolioProject project = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new CustomException(UserErrorCode.PORTFOLIO_NOT_FOUND));

        assertPathUserMatchesOwner(portfolioUserId, project);

        boolean isMine = Objects.equals(userId, project.getUserId());
        if (!isMine && !project.isPublic()) {
            throw new CustomException(UserErrorCode.PORTFOLIO_FORBIDDEN);
        }

        String thumbUrl = cdnOrDefault(project.getThumbnailUrl());
        PortfolioProjectDto projectDto = PortfolioProjectDto.from(project, thumbUrl);

        List<PortfolioAsset> assets = portfolioAssetRepository.findAssetsByPortfolioId(portfolioId);

        List<String> keys = assets.stream()
                .map(PortfolioAsset::getFileKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        Map<String, UploadTicket> ticketMap = keys.isEmpty()
                ? Map.of()
                : uploadTicketRepository.findAllByStorageKeyIn(keys).stream()
                .collect(Collectors.toMap(UploadTicket::getStorageKey, t -> t));

        Map<String, String> urlMap = new HashMap<>();
        for (String key : keys) {
            UploadTicket t = ticketMap.get(key);
            String filename = (t == null) ? null : t.getOriginalFilename();
            String contentType = (t == null) ? null : t.getContentType();

            try {
                String url = presignEngine.presignDownload(key, filename, contentType).downloadUrl();
                urlMap.put(key, url);
            } catch (Exception e) {
                log.warn("presignDownload failed. portfolioId={}, key={}", portfolioId, key, e);
                urlMap.put(key, null);
            }
        }
        List<PortfolioAssetView> views = assets.stream()
                .map(a -> new PortfolioAssetView(
                        a.getAssetId(),
                        a.getType(),
                        a.getFileKey(),
                        urlMap.get(a.getFileKey()),
                        a.getSortOrder(),
                        a.getCreatedAt()
                ))
                .toList();

        /// 글쓴이 프로필
        Long authorId = Optional.ofNullable(project.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_ERROR));

        AuthorDto author = authorAssembler.buildAuthorMap(List.of(authorId))
                .get(authorId);

        return PortfolioResponse.of(isMine, new PortfolioDetailResponse(author, projectDto, views));
    }

    @Transactional
    public PortfolioPreviewResponse create(Long userId, Long portfolioUserId, PortfolioRequest request) {

        if (!Objects.equals(userId, portfolioUserId)) throw new CustomException(UserErrorCode.PORTFOLIO_FORBIDDEN);
        userRepository.lockUserRow(userId);
        requireAuthenticatedUser(userId);

        if (!StringUtils.hasText(request.thumbnailKey())) {
            throw new CustomException(UserErrorCode.PORTFOLIO_THUMBNAIL_REQUIRED);
        }

        // 1. 엔티티 생성 시 누락된 필드(assignedRole, techStack) 추가
        PortfolioProject project = PortfolioProject.builder()
                .userId(userId)
                .title(request.projectTitle())
                .description(request.description())
                .thumbnailUrl(DEFAULT_THUMB)
                .startDate(request.startedAt())
                .endDate(request.endedAt())
                .review(request.review())
                .assignedRole(StringUtils.hasText(request.project_role())
                        ? new ArrayList<>(List.of(request.project_role()))
                        : new ArrayList<>())
                .techStack(new ArrayList<>(request.techStack()))
                .isPublic(true)
                .createdAt(LocalDate.now())
                .updatedAt(LocalDate.now())
                .build();

        PortfolioProject saved = portfolioRepository.save(project);

        // 3. S3 관련 키가 존재하는 경우에만 로직 수행
        // thumbnailKey나 attachmentKeys가 둘 다 없으면 skip
        boolean hasThumbnail = StringUtils.hasText(request.thumbnailKey());
        boolean hasAttachments = request.attachmentKeys() != null && !request.attachmentKeys().isEmpty();

        if (hasThumbnail || hasAttachments) {
            portfolioAttachmentService.applyOnCreate(
                    saved,
                    userId,
                    request.thumbnailKey(),
                    request.attachmentKeys()
            );
        }

        return new PortfolioPreviewResponse(
                saved.getPortfolioId(),
                saved.getTitle(),
                cdnOrDefault(saved.getThumbnailUrl()),
                saved.isPublic(),
                saved.isFavorite(),
                saved.getUpdatedAt()
        );
    }

    @Transactional
    public PortfolioPreviewResponse update(Long userId, Long portfolioUserId, Long portfolioId, PortfolioRequest request) {
        requireAuthenticatedUser(userId);
        PortfolioProject project = portfolioRepository.findByIdForUpdate(portfolioId)
                .orElseThrow(() -> new CustomException(UserErrorCode.PORTFOLIO_NOT_FOUND));

        // 권한 체크
        assertPathUserMatchesOwner(portfolioUserId, project);
        assertOwner(userId, project);

        // 1. 일반 정보 업데이트 (프로젝트 역할 및 기술 스택 포함)
        project.updateInfo(
                request.projectTitle(),
                request.description(),
                request.review(),
                request.startedAt(),
                request.endedAt(),
                request.project_role(), // 추가
                request.techStack()     // 추가
        );

        // 2. S3 첨부파일/썸네일 업데이트 로직 (Null 및 빈 값 방어)
        boolean thumbTouched = request.thumbnailKey() != null;         // null 아니면 변경 의도 있음 ("" 포함)
        boolean assetsTouched = request.attachmentKeys() != null;      // null 아니면 교체/삭제 의도 있음 ([] 포함)

        if (thumbTouched || assetsTouched) {
            portfolioAttachmentService.applyOnUpdate(
                    project,
                    userId,
                    request.thumbnailKey(),
                    request.attachmentKeys()
            );
        }

        return new PortfolioPreviewResponse(
                project.getPortfolioId(),
                project.getTitle(),
                cdnOrDefault(project.getThumbnailUrl()),
                project.isPublic(),
                project.isFavorite(),
                project.getUpdatedAt()
        );
    }

    @Transactional
    public void delete(Long userId, Long portfolioUserId, Long portfolioId) {
        requireAuthenticatedUser(userId);
        PortfolioProject project = portfolioRepository.findByIdForUpdate(portfolioId)
                .orElseThrow(() -> new CustomException(UserErrorCode.PORTFOLIO_NOT_FOUND));

        assertPathUserMatchesOwner(portfolioUserId, project);

        boolean isAdmin = userRepository.existsByUserIdAndRole(userId, UserRole.ADMIN);
        boolean isOwner = Objects.equals(project.getUserId(), userId);

        if (!isOwner && !isAdmin) throw new CustomException(UserErrorCode.PORTFOLIO_FORBIDDEN);

        portfolioAttachmentService.deleteAllFilesAfterCommit(project);
        portfolioRepository.delete(project);
    }

    @Transactional
    public boolean togglePublic(Long userId, Long portfolioUserId, Long portfolioId) {
        requireAuthenticatedUser(userId);
        PortfolioProject project = portfolioRepository.findByIdForUpdate(portfolioId)
                .orElseThrow(() -> new CustomException(UserErrorCode.PORTFOLIO_NOT_FOUND));

        assertPathUserMatchesOwner(portfolioUserId, project);
        assertOwner(userId, project);

        project.setPublic(!project.isPublic());
        project.setUpdatedAt(LocalDate.now());
        return project.isPublic();
    }

    @Transactional
    public boolean toggleFavorite(Long userId, Long portfolioUserId, Long portfolioId) {
        requireAuthenticatedUser(userId);
        PortfolioProject project = portfolioRepository.findByIdForUpdate(portfolioId)
                .orElseThrow(() -> new CustomException(UserErrorCode.PORTFOLIO_NOT_FOUND));

        assertPathUserMatchesOwner(portfolioUserId, project);
        assertOwner(userId, project);

        project.setFavorite(!project.isFavorite());
        project.setUpdatedAt(LocalDate.now());
        return project.isFavorite();
    }

    /* util
    */

    private void assertPathUserMatchesOwner(Long portfolioUserId, PortfolioProject project) {
        if (!Objects.equals(project.getUserId(), portfolioUserId)) throw new CustomException(UserErrorCode.PORTFOLIO_NOT_FOUND);
    }

    private void assertOwner(Long userId, PortfolioProject project) {
        if (!Objects.equals(project.getUserId(), userId)) throw new CustomException(UserErrorCode.PORTFOLIO_FORBIDDEN);
    }

    private Users requireAuthenticatedUser(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        return user;
    }

    private String cdnOrDefault(String key) {
        String safeKey = StringUtils.hasText(key) && !DEFAULT_THUMB.equals(key.trim())
                ? key
                : DEFAULT_THUMB_KEY;
        try {
            String url = publicUrlIssuer.issuePublicUrl(safeKey);
            return StringUtils.hasText(url) ? url : publicUrlIssuer.issuePublicUrl(DEFAULT_THUMB_KEY);
        } catch (Exception e) {
            log.warn("issuePublicUrl failed. key={}", safeKey, e);
            // 최후의 최후: 하드코딩 URL로라도 반환 (여기서도 null 금지)
            return "https://cdn.camnect.site/" + DEFAULT_THUMB_KEY;
        }
    }
}
