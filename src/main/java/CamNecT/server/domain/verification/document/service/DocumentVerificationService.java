package CamNecT.server.domain.verification.document.service;

import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.verification.document.repository.DocumentVerificationSubmissionRepository;
import CamNecT.server.domain.verification.document.config.DocumentVerificationProperties;
import CamNecT.server.domain.verification.document.dto.DocumentVerificationDetailResponse;
import CamNecT.server.domain.verification.document.dto.DocumentVerificationFileDto;
import CamNecT.server.domain.verification.document.dto.DocumentVerificationListItemResponse;
import CamNecT.server.domain.verification.document.dto.SubmitDocumentVerificationResponse;
import CamNecT.server.domain.verification.document.model.DocumentType;
import CamNecT.server.domain.verification.document.model.DocumentVerificationSubmission;
import CamNecT.server.domain.verification.document.model.VerificationStatus;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.StorageErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.VerificationErrorCode;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.dto.request.PresignUploadRequest;
import CamNecT.server.global.storage.dto.response.PresignDownloadResponse;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DocumentVerificationService {

    private final DocumentVerificationProperties props;

    private final DocumentVerificationSubmissionRepository submissionRepo;
    private final UserRepository userRepository;

    private final PresignEngine presignEngine;
    private final UploadTicketRepository ticketRepo;
    private final GlobalPresignMethods globalPresignMethods;

    // ===== presign upload =====
    @Transactional
    public PresignUploadResponse presignUpload(Long userId, PresignUploadRequest req) {
        requirePendingUserForUpdate(userId);

        String ct = normalize(req.contentType());

        if (req.size() <= 0) throw new CustomException(VerificationErrorCode.EMPTY_FILE_NOT_ALLOWED);
        if (req.size() > props.maxFileSizeBytes()) throw new CustomException(VerificationErrorCode.FILE_TOO_LARGE);
        if (!StringUtils.hasText(ct) || !props.getAllowedContentTypes().contains(ct)) {
            throw new CustomException(VerificationErrorCode.UNSUPPORTED_CONTENT_TYPE);
        }

        String keyPrefix = "verification/user-" + userId + "/documents";

        // 티켓 만료 정리 + active pending 제한은 PresignEngine.issueUpload()가 담당
        return presignEngine.issueUpload(
                userId,
                UploadPurpose.VERIFICATION_DOCUMENT,
                keyPrefix,
                ct,
                req.size(),
                req.originalFilename()
        );
    }

    // ===== submit (단일 key) =====
    @Transactional
    public SubmitDocumentVerificationResponse submit(Long userId, DocumentType docType, String documentKey) {

        if (!StringUtils.hasText(documentKey)) {
            throw new CustomException(VerificationErrorCode.DOCUMENTS_REQUIRED);
        }

        requirePendingUserForUpdate(userId);

        DocumentVerificationSubmission oldPending = submissionRepo
                .findTopByUserIdAndStatusOrderBySubmittedAtDesc(userId, VerificationStatus.PENDING)
                .orElse(null);

        if(oldPending != null) throw new CustomException(VerificationErrorCode.PENDING_ALREADY_EXISTS);

        UploadTicket t = ticketRepo.findByStorageKey(documentKey)
                .orElseThrow(() -> new CustomException(StorageErrorCode.UPLOAD_TICKET_NOT_FOUND));
        if (!t.getUserId().equals(userId)) {
            throw new CustomException(StorageErrorCode.UPLOAD_TICKET_FORBIDDEN);
        }

        String ct = normalize(t.getContentType());
        if (!StringUtils.hasText(ct)) {
            throw new CustomException(VerificationErrorCode.UNSUPPORTED_CONTENT_TYPE);
        }

        DocumentVerificationSubmission sub = DocumentVerificationSubmission.builder()
                .userId(userId)
                .docType(docType)
                .status(VerificationStatus.PENDING)
                .submittedAt(LocalDateTime.now())
                .build();

        sub.attachFile(
                documentKey,
                safeName(t.getOriginalFilename()),
                ct,
                t.getSize()
        );

        submissionRepo.save(sub);

        String finalPrefix = "verification/submission-" + sub.getId() + "/documents";

        String finalKey = presignEngine.consume(
                userId,
                UploadPurpose.VERIFICATION_DOCUMENT,
                UploadRefType.VERIFICATION,
                sub.getId(),
                documentKey,
                finalPrefix
        );

        sub.attachFile(
                finalKey,
                safeName(t.getOriginalFilename()),
                normalize(t.getContentType()),
                t.getSize()
        );

        sub.replaceStorageKey(finalKey);

        return new SubmitDocumentVerificationResponse(sub.getId(), sub.getStatus(), sub.getSubmittedAt());
    }

    @Transactional(readOnly = true)
    public List<DocumentVerificationListItemResponse> mySubmissions(Long userId) {
        return submissionRepo.findByUserIdOrderBySubmittedAtDesc(userId).stream()
                .map(r -> new DocumentVerificationListItemResponse(
                        r.getId(), r.getDocType(), r.getStatus(), r.getSubmittedAt(), r.getReviewedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentVerificationDetailResponse mySubmissionDetail(Long userId, Long submissionId) {
        DocumentVerificationSubmission r = submissionRepo.findByIdAndUserId(submissionId, userId)
                .orElseThrow(() -> new CustomException(VerificationErrorCode.SUBMISSION_NOT_FOUND));

        var file = new DocumentVerificationFileDto(
                r.getOriginalFilename(),
                r.getContentType(),
                r.getSize(),
                r.getStorageKey()
        );

        return new DocumentVerificationDetailResponse(
                r.getId(), r.getDocType(), r.getStatus(),
                r.getSubmittedAt(), r.getReviewedAt(), r.getRejectReason(),
                file
        );
    }

    @Transactional(readOnly = true)
    public PresignDownloadResponse myDownloadUrl(Long userId, Long submissionId) {
        DocumentVerificationSubmission r = submissionRepo.findByIdAndUserId(submissionId, userId)
                .orElseThrow(() -> new CustomException(VerificationErrorCode.SUBMISSION_NOT_FOUND));

        if (!StringUtils.hasText(r.getStorageKey())) {
            throw new CustomException(VerificationErrorCode.FILE_NOT_FOUND);
        }

        String filename = safeName(r.getOriginalFilename());
        String ct = normalize(r.getContentType());
        return presignEngine.presignDownload(r.getStorageKey(), filename, ct);
    }

    @Transactional
    public void cancel(Long userId, Long submissionId) {
        DocumentVerificationSubmission r = submissionRepo.findByIdAndUserId(submissionId, userId)
                .orElseThrow(() -> new CustomException(VerificationErrorCode.SUBMISSION_NOT_FOUND));

        if (r.getStatus() != VerificationStatus.PENDING) {
            throw new CustomException(VerificationErrorCode.ONLY_PENDING_CAN_REVIEW);
        }

        String key = r.getStorageKey();
        r.cancel();

        if (StringUtils.hasText(key)) {
            globalPresignMethods.deleteAfterCommit(Collections.singleton(key));
        }
    }

    private Users requirePendingUserForUpdate(Long userId) {
        Users user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(AuthErrorCode.USER_WITHDRAWN);
        }
        if (user.getStatus() != UserStatus.ADMIN_PENDING) {
            throw new CustomException(VerificationErrorCode.DOCUMENT_SUBMISSION_NOT_ALLOWED);
        }
        return user;
    }

    private String safeName(String name) {
        return StringUtils.hasText(name) ? name : "file";
    }

    private String normalize(String ct) {
        return (ct == null) ? "" : ct.trim().toLowerCase(Locale.ROOT);
    }
}
