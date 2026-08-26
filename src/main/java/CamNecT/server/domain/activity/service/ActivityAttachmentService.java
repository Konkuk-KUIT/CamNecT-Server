package CamNecT.server.domain.activity.service;

import CamNecT.server.domain.activity.model.props.ActivityAttachmentProps;
import CamNecT.server.domain.activity.model.props.ActivityThumbnailProps;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.StorageErrorCode;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.dto.request.PresignUploadBatchRequest;
import CamNecT.server.global.storage.dto.request.PresignUploadRequest;
import CamNecT.server.global.storage.dto.response.PresignUploadBatchResponse;
import CamNecT.server.global.storage.dto.response.PresignUploadResponse;
import CamNecT.server.global.storage.model.UploadPurpose;
import CamNecT.server.global.storage.model.UploadTicket;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.PresignEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ActivityAttachmentService {

    protected static final Set<String> THUMB_ALLOWED = Set.of("image/jpeg","image/png","image/webp");

    private final AccountAccessGuard accountAccessGuard;
    private final PresignEngine presignEngine;
    private final UploadTicketRepository ticketRepo;
    private final GlobalPresignMethods globalPresignMethods;

    private final ActivityAttachmentProps attachmentProps;
    private final ActivityThumbnailProps thumbnailProps;

    @Transactional
    public PresignUploadResponse presignThumbnail(Long userId, PresignUploadRequest req) {
        accountAccessGuard.requireAccessibleForUpdate(userId);

        String ct = globalPresignMethods.normalize(req.contentType());
        if (!StringUtils.hasText(ct)) throw new CustomException(StorageErrorCode.UNSUPPORTED_CONTENT_TYPE);

        if (req.size() == null || req.size() <= 0) throw new CustomException(StorageErrorCode.EMPTY_FILE_NOT_ALLOWED);
        if (req.size() > thumbnailProps.maxFileSizeBytes()) throw new CustomException(StorageErrorCode.FILE_TOO_LARGE);

        if (!THUMB_ALLOWED.contains(ct)) throw new CustomException(StorageErrorCode.UNSUPPORTED_CONTENT_TYPE);

        String prefix = "activity/user-" + userId + "/thumbnail";
        return presignEngine.issueUpload(
                userId,
                UploadPurpose.ACTIVITY_THUMBNAIL,
                prefix,
                ct,
                req.size(),
                req.originalFilename()
        );
    }

    @Transactional
    public PresignUploadBatchResponse presignAttachmentsBatch(Long userId, PresignUploadBatchRequest req) {
        List<PresignUploadBatchRequest.Item> items =
                (req == null || req.items() == null) ? List.of()
                        : req.items().stream().filter(Objects::nonNull).toList();

        if (items.isEmpty()) throw new CustomException(StorageErrorCode.EMPTY_FILE_NOT_ALLOWED);
        if (items.size() > attachmentProps.maxFiles()) throw new CustomException(StorageErrorCode.UPLOAD_TICKET_LIMIT_EXCEEDED);

        accountAccessGuard.requireAccessibleForUpdate(userId);

        LocalDateTime now = LocalDateTime.now();
        ticketRepo.bulkExpirePendingByUserPurpose(userId, UploadPurpose.ACTIVITY_ATTACHMENT, now);

        long pendingActive = ticketRepo.countByUserIdAndPurposeAndStatusAndExpiresAtAfter(
                userId, UploadPurpose.ACTIVITY_ATTACHMENT, UploadTicket.Status.PENDING, now
        );
        if (pendingActive + items.size() > attachmentProps.maxFiles()) {
            throw new CustomException(StorageErrorCode.UPLOAD_TICKET_LIMIT_EXCEEDED);
        }

        Set<String> allow = attachmentProps.allowedSet();

        String prefix = "activity/user-" + userId + "/attachments";
        List<PresignEngine.IssueItem> issueItems = new ArrayList<>(items.size());

        for (var item : items) {
            String ct = globalPresignMethods.normalize(item.contentType());
            if (!StringUtils.hasText(ct)) throw new CustomException(StorageErrorCode.UNSUPPORTED_CONTENT_TYPE);
            if (!allow.isEmpty() && !allow.contains(ct)) throw new CustomException(StorageErrorCode.UNSUPPORTED_CONTENT_TYPE);
            if (item.size() <= 0) throw new CustomException(StorageErrorCode.EMPTY_FILE_NOT_ALLOWED);
            if (item.size() > attachmentProps.maxFileSizeBytes()) throw new CustomException(StorageErrorCode.FILE_TOO_LARGE);

            issueItems.add(new PresignEngine.IssueItem(ct, item.size(), item.originalFilename()));
        }

        return new PresignUploadBatchResponse(
                presignEngine.issueUploadBatch(
                        userId,
                        UploadPurpose.ACTIVITY_ATTACHMENT,
                        prefix,
                        issueItems,
                        attachmentProps.maxFiles()
                )
        );
    }
}
