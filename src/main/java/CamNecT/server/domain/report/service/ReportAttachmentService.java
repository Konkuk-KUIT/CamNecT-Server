package CamNecT.server.domain.report.service;

import CamNecT.server.domain.report.model.props.ReportEvidenceProps;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.StorageErrorCode;
import CamNecT.server.global.storage.dto.request.PresignUploadRequest;
import CamNecT.server.global.storage.dto.response.PresignUploadResponse;
import CamNecT.server.global.storage.model.UploadPurpose;
import CamNecT.server.global.storage.model.UploadRefType;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.service.PresignEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 신고 증거 이미지 업로드 관련 서비스
 * - Presigned URL 발급
 * - 파일 유효성 검증
 * - UploadTicket 관리
 */
@Service
@RequiredArgsConstructor
public class ReportAttachmentService {

    private static final Set<String> EVIDENCE_ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository userRepository;
    private final PresignEngine presignEngine;
    private final UploadTicketRepository ticketRepo;
    private final GlobalPresignMethods globalPresignMethods;
    private final ReportEvidenceProps evidenceProps;

    /**
     * 신고 증거 이미지 업로드용 Presigned URL 발급
     * - 이미지 타입만 허용 (jpg, png, webp)
     * - temp 경로로 발급됨
     * - 사용자 인증 및 상태 확인
     */
    @Transactional
    public PresignUploadResponse presignEvidence(Long userId, PresignUploadRequest req) {
        // 사용자 인증 및 상태 확인
        lockAuthenticatedUser(userId);

        // 파일 크기 검증
        if (req.size() == null || req.size() <= 0) {
            throw new CustomException(StorageErrorCode.EMPTY_FILE_NOT_ALLOWED);
        }
        if (req.size() > evidenceProps.maxFileSizeBytes()) {
            throw new CustomException(StorageErrorCode.FILE_TOO_LARGE);
        }

        // Content-Type 정규화 및 검증
        String ct = globalPresignMethods.normalize(req.contentType());
        if (!EVIDENCE_ALLOWED.contains(ct)) {
            throw new CustomException(StorageErrorCode.UNSUPPORTED_CONTENT_TYPE);
        }

        // Presigned URL 발급
        String prefix = "reports/user-" + userId + "/evidence";

        return presignEngine.issueUpload(
                userId,
                UploadPurpose.REPORT_EVIDENCE,
                prefix,
                ct,
                req.size(),
                req.originalFilename()
        );
    }

    /**
     * 신고 생성 후 증거 이미지 처리
     * - temp에서 최종 경로로 이동
     * - UploadTicket consume
     */
    @Transactional
    public String applyOnReportCreate(Long userId, Long reportId, String evidenceKey) {
        if (evidenceKey == null || evidenceKey.isBlank()) {
            return null;
        }

        String finalPrefix = "reports/user-" + userId + "/report-" + reportId + "/evidence";

        // UploadTicket consume 및 경로 이동
        return presignEngine.consume(
                userId,
                UploadPurpose.REPORT_EVIDENCE,
                UploadRefType.REPORT,
                reportId,
                evidenceKey,
                finalPrefix
        );
    }

    /**
     * 사용자 인증 및 상태 확인
     */
    private Users lockAuthenticatedUser(Long userId) {
        userRepository.lockUserRow(userId);
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        return user;
    }
}
