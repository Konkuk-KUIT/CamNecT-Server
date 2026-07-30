package CamNecT.server.domain.report.service;

import CamNecT.server.domain.report.model.Report;
import CamNecT.server.domain.report.model.ReportEvidence;
import CamNecT.server.domain.report.model.props.ReportEvidenceProps;
import CamNecT.server.domain.report.repository.ReportEvidenceRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.StorageErrorCode;
import CamNecT.server.global.storage.dto.request.PresignUploadBatchRequest;
import CamNecT.server.global.storage.dto.response.PresignUploadBatchResponse;
import CamNecT.server.global.storage.model.UploadPurpose;
import CamNecT.server.global.storage.model.UploadRefType;
import CamNecT.server.global.storage.model.UploadTicket;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.service.PresignEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportAttachmentService {

    private static final Set<String> EVIDENCE_ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository userRepository;
    private final PresignEngine presignEngine;
    private final UploadTicketRepository ticketRepo;
    private final ReportEvidenceRepository evidenceRepository;
    private final GlobalPresignMethods globalPresignMethods;
    private final ReportEvidenceProps evidenceProps;

    @Transactional
    public PresignUploadBatchResponse presignEvidenceBatch(Long userId, PresignUploadBatchRequest req) {
        List<PresignUploadBatchRequest.Item> items = req == null || req.items() == null
                ? List.of()
                : req.items();

        if (items.isEmpty()) {
            throw new CustomException(StorageErrorCode.EMPTY_FILE_NOT_ALLOWED);
        }
        if (items.size() > evidenceProps.maxFiles()) {
            throw new CustomException(StorageErrorCode.UPLOAD_TICKET_LIMIT_EXCEEDED);
        }
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new CustomException(StorageErrorCode.INVALID_ATTACHMENT_METADATA);
        }

        lockAuthenticatedUser(userId);

        List<PresignEngine.IssueItem> issueItems = new ArrayList<>(items.size());
        for (PresignUploadBatchRequest.Item item : items) {
            String contentType = validateEvidenceItem(item);
            issueItems.add(new PresignEngine.IssueItem(contentType, item.size(), item.originalFilename()));
        }

        return new PresignUploadBatchResponse(presignEngine.issueUploadBatch(
                userId,
                UploadPurpose.REPORT_EVIDENCE,
                "reports/user-" + userId + "/evidence",
                issueItems,
                evidenceProps.maxFiles()
        ));
    }

    @Transactional
    public List<ReportEvidence> applyOnReportCreate(Long userId, Report report, List<String> evidenceKeys) {
        if (evidenceKeys == null || evidenceKeys.isEmpty()) {
            return List.of();
        }
        if (evidenceKeys.size() > evidenceProps.maxFiles()) {
            throw new CustomException(StorageErrorCode.UPLOAD_TICKET_LIMIT_EXCEEDED);
        }

        Set<String> uniqueKeys = new HashSet<>();
        for (String evidenceKey : evidenceKeys) {
            if (!StringUtils.hasText(evidenceKey) || evidenceKey.length() > 500) {
                throw new CustomException(StorageErrorCode.INVALID_ATTACHMENT_METADATA);
            }
            if (!uniqueKeys.add(evidenceKey)) {
                throw new CustomException(StorageErrorCode.DUPLICATE_ATTACHMENT_KEY);
            }
        }

        Map<String, UploadTicket> ticketsByKey = ticketRepo.findAllByStorageKeyIn(evidenceKeys).stream()
                .collect(Collectors.toMap(UploadTicket::getStorageKey, Function.identity()));
        String finalPrefix = "reports/user-" + userId + "/report-" + report.getReportId() + "/evidence";
        List<ReportEvidence> evidence = new ArrayList<>(evidenceKeys.size());

        for (int order = 0; order < evidenceKeys.size(); order++) {
            String evidenceKey = evidenceKeys.get(order);
            UploadTicket ticket = ticketsByKey.get(evidenceKey);
            if (ticket == null) {
                throw new CustomException(StorageErrorCode.UPLOAD_TICKET_NOT_FOUND);
            }

            String finalKey = presignEngine.consume(
                    userId,
                    UploadPurpose.REPORT_EVIDENCE,
                    UploadRefType.REPORT,
                    report.getReportId(),
                    evidenceKey,
                    finalPrefix
            );
            evidence.add(ReportEvidence.create(
                    report,
                    finalKey,
                    ticket.getOriginalFilename(),
                    ticket.getContentType(),
                    ticket.getSize(),
                    order
            ));
        }

        return evidenceRepository.saveAll(evidence);
    }

    private String validateEvidenceItem(PresignUploadBatchRequest.Item item) {
        if (!StringUtils.hasText(item.originalFilename())
                || item.originalFilename().length() > 255
                || item.originalFilename().chars().anyMatch(Character::isISOControl)
                || item.originalFilename().contains("/")
                || item.originalFilename().contains("\\")) {
            throw new CustomException(StorageErrorCode.INVALID_ATTACHMENT_METADATA);
        }
        if (item.size() <= 0) {
            throw new CustomException(StorageErrorCode.EMPTY_FILE_NOT_ALLOWED);
        }
        if (item.size() > evidenceProps.maxFileSizeBytes()) {
            throw new CustomException(StorageErrorCode.FILE_TOO_LARGE);
        }

        String contentType = globalPresignMethods.normalize(item.contentType());
        if (!EVIDENCE_ALLOWED.contains(contentType)) {
            throw new CustomException(StorageErrorCode.UNSUPPORTED_CONTENT_TYPE);
        }
        return contentType;
    }

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
