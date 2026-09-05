package CamNecT.server.domain.verification.document.dto;

import CamNecT.server.domain.verification.document.model.DocumentType;
import CamNecT.server.domain.verification.document.model.VerificationStatus;

import java.time.LocalDateTime;

public record AdminDocumentVerificationDetailResponse(
        Long submissionId,
        VerificationStatus status,
        DocumentType docType,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String rejectReason,

        Long userId,
        String username,
        String email,
        String name,

        String studentNo,
        Integer yearLevel,
        Long institutionId,
        Long majorId,

        String originalFilename,
        String contentType,
        long size
) {}
