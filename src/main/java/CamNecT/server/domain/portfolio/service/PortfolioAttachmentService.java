package CamNecT.server.domain.portfolio.service;

import CamNecT.server.domain.portfolio.model.PortfolioAsset;
import CamNecT.server.domain.portfolio.model.PortfolioProject;
import CamNecT.server.domain.portfolio.model.props.PortfolioAssetProps;
import CamNecT.server.domain.portfolio.model.props.PortfolioThumbnailProps;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.StorageErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.dto.request.PresignUploadBatchRequest;
import CamNecT.server.global.storage.dto.request.PresignUploadRequest;
import CamNecT.server.global.storage.dto.response.PresignUploadBatchResponse;
import CamNecT.server.global.storage.dto.response.PresignUploadResponse;
import CamNecT.server.global.storage.model.UploadPurpose;
import CamNecT.server.global.storage.model.UploadRefType;
import CamNecT.server.global.storage.model.UploadTicket;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.PresignEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioAttachmentService {

    private static final String DEFAULT_THUMB = "기본이미지";
    private static final Set<String> THUMB_ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

    private final AccountAccessGuard accountAccessGuard;

    private final PresignEngine presignEngine;
    private final UploadTicketRepository ticketRepo;
    private final GlobalPresignMethods globalPresignMethods;

    private final PortfolioAssetProps assetProps;
    private final PortfolioThumbnailProps thumbnailProps;

    /**
     * 썸네일 업로드 presign (단건)
     * - 이미지 타입만 허용
     * - temp 경로로 발급됨
     */
    @Transactional
    public PresignUploadResponse presignThumbnail(Long userId, Long portfolioUserId, PresignUploadRequest req) {
        validateOwner(userId, portfolioUserId);
        accountAccessGuard.requireAccessibleForUpdate(userId);

        String ct = globalPresignMethods.normalize(req.contentType());
        if (req.size() == null || req.size() <= 0) throw new CustomException(StorageErrorCode.EMPTY_FILE_NOT_ALLOWED);
        if (req.size() > thumbnailProps.maxFileSizeBytes()) throw new CustomException(StorageErrorCode.FILE_TOO_LARGE);
        if (!THUMB_ALLOWED.contains(ct)) throw new CustomException(StorageErrorCode.UNSUPPORTED_CONTENT_TYPE);

        String prefix = "portfolio/user-" + userId + "/thumbnail";

        return presignEngine.issueUpload(
                userId,
                UploadPurpose.PORTFOLIO_THUMBNAIL,
                prefix,
                ct,
                req.size(),
                req.originalFilename()
        );
    }

    /**
     * assets 업로드 presign (다건)
     * - pdf 포함 가능(allowedContentTypes에 있으면)
     */
    @Transactional
    public PresignUploadBatchResponse presignAssetsBatch(Long userId, Long portfolioUserId, PresignUploadBatchRequest req) {
        validateOwner(userId, portfolioUserId);

        var items = (req == null || req.items() == null)
                ? List.<PresignUploadBatchRequest.Item>of()
                : req.items().stream().filter(Objects::nonNull).toList();

        if (items.isEmpty()) throw new CustomException(StorageErrorCode.EMPTY_FILE_NOT_ALLOWED);
        if (items.size() > assetProps.maxFiles()) throw new CustomException(StorageErrorCode.UPLOAD_TICKET_LIMIT_EXCEEDED);

        accountAccessGuard.requireAccessibleForUpdate(userId);

        String prefix = "portfolio/user-" + userId + "/assets";

        List<PresignEngine.IssueItem> issueItems = new ArrayList<>(items.size());
        for (var item : items) {
            String ct = globalPresignMethods.normalize(item.contentType());

            if (item.size() <= 0) throw new CustomException(StorageErrorCode.EMPTY_FILE_NOT_ALLOWED);
            if (item.size() > assetProps.maxFileSizeBytes()) throw new CustomException(StorageErrorCode.FILE_TOO_LARGE);
            if (!hasText(ct) || !assetProps.allowedContentTypes().contains(ct)) {
                throw new CustomException(StorageErrorCode.UNSUPPORTED_CONTENT_TYPE);
            }

            issueItems.add(new PresignEngine.IssueItem(ct, item.size(), item.originalFilename()));
        }

        return new PresignUploadBatchResponse(
                presignEngine.issueUploadBatch(
                        userId,
                        UploadPurpose.PORTFOLIO_ATTACHMENT,
                        prefix,
                        issueItems,
                        assetProps.maxFiles()
                )
        );
    }

    /**
     * create 직후: thumbnailKey/attachmentKeys consume + entity 반영
     * - thumbnail은 이미지 key만 허용
     * - attachmentKeys에 thumbnailKey가 섞여 들어오면 무시(중복 consume 방지)
     */
    @Transactional
    public void applyOnCreate(PortfolioProject project, Long userId, String thumbnailKey, List<String> attachmentKeys) {

        String finalThumbPrefix = "portfolio/user-" + userId + "/portfolio-" + project.getPortfolioId() + "/thumbnail";
        String finalAssetPrefix = "portfolio/user-" + userId + "/portfolio-" + project.getPortfolioId() + "/assets";

        // thumbnail (create에서는 "있으면 세팅", 없으면 DEFAULT 유지)
        if (hasText(thumbnailKey) && !DEFAULT_THUMB.equals(thumbnailKey)) {
            ensureThumbnailIsImage(thumbnailKey);
            String finalKey = consume(userId, project.getPortfolioId(), thumbnailKey,
                    finalThumbPrefix,UploadPurpose.PORTFOLIO_THUMBNAIL);
            project.updateThumbnail(finalKey);
        }

        // assets (thumbKey는 제거됨 - 중복 consume 방지)
        LinkedHashSet<String> reqKeys = distinctKeys(attachmentKeys, thumbnailKey);
        if (reqKeys.size() > assetProps.maxFiles()) {
            throw new CustomException(StorageErrorCode.UPLOAD_TICKET_LIMIT_EXCEEDED);
        }
        int order = 1;

        for (String k : reqKeys) {
            String finalKey = consume(userId, project.getPortfolioId(), k,
                    finalAssetPrefix, UploadPurpose.PORTFOLIO_ATTACHMENT);

            UploadTicket t = ticketRepo.findByStorageKey(finalKey)
                    .orElseThrow(() -> new CustomException(StorageErrorCode.UPLOAD_TICKET_NOT_FOUND));

            project.getAssets().add(PortfolioAsset.builder()
                    .portfolioProject(project)
                    .type(t.getContentType())
                    .fileKey(finalKey)
                    .sortOrder(order++)
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    /**
     * update: thumbnail 교체 + assets 교체(attachmentKeys가 null이 아니면)
     */
    @Transactional
    public void applyOnUpdate(PortfolioProject project, Long userId, String newThumbnailKey, List<String> newAttachmentKeys) {
        Set<String> deleteAfterCommit = new HashSet<>();

        String finalThumbPrefix = "portfolio/user-" + userId + "/portfolio-" + project.getPortfolioId() + "/thumbnail";
        String finalAssetPrefix = "portfolio/user-" + userId + "/portfolio-" + project.getPortfolioId() + "/assets";

        // thumbnail 교체(요청이 있고, 기존과 다를 때만)
        if (newThumbnailKey != null) { // null이면 유지
            if (!hasText(newThumbnailKey) || DEFAULT_THUMB.equals(newThumbnailKey)) {
                // 삭제 요청(또는 기본이미지로 명시)
                String old = project.getThumbnailUrl();
                if (hasText(old) && !DEFAULT_THUMB.equals(old)) deleteAfterCommit.add(old);
                project.updateThumbnail(DEFAULT_THUMB);
            } else if (!Objects.equals(newThumbnailKey, project.getThumbnailUrl())) {
                // 교체
                ensureThumbnailIsImage(newThumbnailKey);

                String old = project.getThumbnailUrl();
                if (hasText(old) && !DEFAULT_THUMB.equals(old)) deleteAfterCommit.add(old);

                String finalKey = consume(userId, project.getPortfolioId(), newThumbnailKey, finalThumbPrefix, UploadPurpose.PORTFOLIO_THUMBNAIL);
                project.updateThumbnail(finalKey);
            }
        }

        // assets 교체(요청이 들어온 경우만)
        if (newAttachmentKeys != null) {
            Map<String, PortfolioAsset> currentByKey = project.getAssets().stream()
                    .filter(a -> hasText(a.getFileKey()))
                    .collect(Collectors.toMap(PortfolioAsset::getFileKey, a -> a, (a, b) -> a));

            LinkedHashSet<String> reqKeys = new LinkedHashSet<>();
            for (String k : newAttachmentKeys) {
                if (hasText(k)) reqKeys.add(k);
            }
            if (reqKeys.size() > assetProps.maxFiles()) {
                throw new CustomException(StorageErrorCode.UPLOAD_TICKET_LIMIT_EXCEEDED);
            }

            Set<String> keepKeys = new HashSet<>();
            int order = 1;

            for (String k : reqKeys) {
                PortfolioAsset existing = currentByKey.get(k);
                if (existing != null) {
                    existing.updateSortOrder(order++);
                    keepKeys.add(existing.getFileKey());
                    continue;
                }

                String finalKey = consume(userId, project.getPortfolioId(), k, finalAssetPrefix, UploadPurpose.PORTFOLIO_ATTACHMENT);

                UploadTicket t = ticketRepo.findByStorageKey(finalKey)
                        .orElseThrow(() -> new CustomException(StorageErrorCode.UPLOAD_TICKET_NOT_FOUND));

                project.getAssets().add(PortfolioAsset.builder()
                        .portfolioProject(project)
                        .type(t.getContentType())
                        .fileKey(finalKey)
                        .sortOrder(order++)
                        .createdAt(LocalDateTime.now())
                        .build());

                keepKeys.add(finalKey);
            }

            // 제거될 기존 assets 삭제 예약
            for (String oldKey : currentByKey.keySet()) {
                if (!keepKeys.contains(oldKey)) deleteAfterCommit.add(oldKey);
            }

            // 컬렉션에서 제거(고아삭제)
            project.getAssets().removeIf(a -> {
                String k = a.getFileKey();
                return hasText(k) && !keepKeys.contains(k);
            });
        }

        if (!deleteAfterCommit.isEmpty()) {
            globalPresignMethods.deleteAfterCommit(deleteAfterCommit);
        }
    }

    @Transactional
    public void deleteAllFilesAfterCommit(PortfolioProject project) {
        Set<String> deleteAfterCommit = new HashSet<>();

        String thumb = project.getThumbnailUrl();
        if (hasText(thumb) && !DEFAULT_THUMB.equals(thumb)) deleteAfterCommit.add(thumb);

        for (PortfolioAsset a : project.getAssets()) {
            if (hasText(a.getFileKey())) deleteAfterCommit.add(a.getFileKey());
        }

        globalPresignMethods.deleteAfterCommit(deleteAfterCommit);
    }

    private String consume(Long userId, Long portfolioId, String keyFromClient, String finalPrefix, UploadPurpose purpose) {
        return presignEngine.consume(
                userId,
                purpose,
                UploadRefType.PORTFOLIO,
                portfolioId,
                keyFromClient,
                finalPrefix
        );
    }

    private void validateOwner(Long userId, Long portfolioUserId) {
        if (userId == null || !Objects.equals(userId, portfolioUserId)) {
            throw new CustomException(UserErrorCode.PORTFOLIO_FORBIDDEN);
        }
    }

    private LinkedHashSet<String> distinctKeys(List<String> keys, String thumbnailKey) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (keys == null) return out;
        String cleanThumb = (thumbnailKey != null) ? thumbnailKey.trim() : null;
        for (String k : keys) {
            if (!hasText(k)) continue;
            String cleanK = k.trim();
            if (hasText(cleanThumb) && Objects.equals(cleanK, cleanThumb)) {
                continue;
            }
            out.add(cleanK);
        }
        return out;
    }

    private void ensureThumbnailIsImage(String key) {
        String ct = ticketRepo.findByStorageKey(key)
                .map(UploadTicket::getContentType)
                .map(globalPresignMethods::normalize)
                .orElse(null);

        if (StringUtils.hasText(ct)) {
            if (!THUMB_ALLOWED.contains(ct)) throw new CustomException(StorageErrorCode.UNSUPPORTED_CONTENT_TYPE);
            return;
        }

        // fallback: 확장자
        String k = key.toLowerCase(Locale.ROOT);
        boolean ok = k.endsWith(".jpg") || k.endsWith(".jpeg") || k.endsWith(".png") || k.endsWith(".webp");
        if (!ok) throw new CustomException(StorageErrorCode.UNSUPPORTED_CONTENT_TYPE);
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
