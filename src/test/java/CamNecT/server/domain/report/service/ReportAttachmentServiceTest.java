package CamNecT.server.domain.report.service;

import CamNecT.server.domain.report.model.Report;
import CamNecT.server.domain.report.model.ReportCategory;
import CamNecT.server.domain.report.model.ReportEvidence;
import CamNecT.server.domain.report.model.TargetType;
import CamNecT.server.domain.report.model.props.ReportEvidenceProps;
import CamNecT.server.domain.report.repository.ReportEvidenceRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.storage.dto.request.PresignUploadBatchRequest;
import CamNecT.server.global.storage.dto.response.PresignUploadResponse;
import CamNecT.server.global.storage.model.UploadPurpose;
import CamNecT.server.global.storage.model.UploadRefType;
import CamNecT.server.global.storage.model.UploadTicket;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.service.PresignEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportAttachmentServiceTest {

    @Mock AccountAccessGuard accountAccessGuard;
    @Mock PresignEngine presignEngine;
    @Mock UploadTicketRepository ticketRepository;
    @Mock ReportEvidenceRepository evidenceRepository;
    @Mock GlobalPresignMethods globalPresignMethods;

    private ReportAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new ReportAttachmentService(
                accountAccessGuard,
                presignEngine,
                ticketRepository,
                evidenceRepository,
                globalPresignMethods,
                new ReportEvidenceProps(5, 5)
        );
    }

    @Test
    void presignsMultipleEvidenceImagesAsOneBatch() {
        PresignUploadBatchRequest request = new PresignUploadBatchRequest(List.of(
                new PresignUploadBatchRequest.Item("image/png", 100L, "first.png"),
                new PresignUploadBatchRequest.Item("image/jpeg", 200L, "second.jpg")
        ));
        List<PresignUploadResponse> issued = List.of(
                new PresignUploadResponse("temp/first.png", "https://upload/1", LocalDateTime.now(), Map.of()),
                new PresignUploadResponse("temp/second.jpg", "https://upload/2", LocalDateTime.now(), Map.of())
        );

        when(globalPresignMethods.normalize("image/png")).thenReturn("image/png");
        when(globalPresignMethods.normalize("image/jpeg")).thenReturn("image/jpeg");
        when(presignEngine.issueUploadBatch(
                eq(1L),
                eq(UploadPurpose.REPORT_EVIDENCE),
                eq("reports/user-1/evidence"),
                anyList(),
                eq(5)
        )).thenReturn(issued);

        assertThat(service.presignEvidenceBatch(1L, request).items()).hasSize(2);

        InOrder accessOrder = inOrder(accountAccessGuard, globalPresignMethods, presignEngine);
        accessOrder.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        accessOrder.verify(globalPresignMethods).normalize("image/png");
        accessOrder.verify(presignEngine).issueUploadBatch(
                eq(1L),
                eq(UploadPurpose.REPORT_EVIDENCE),
                eq("reports/user-1/evidence"),
                anyList(),
                eq(5)
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PresignEngine.IssueItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(presignEngine).issueUploadBatch(
                eq(1L),
                eq(UploadPurpose.REPORT_EVIDENCE),
                eq("reports/user-1/evidence"),
                captor.capture(),
                eq(5)
        );
        assertThat(captor.getValue()).extracting(PresignEngine.IssueItem::originalFilename)
                .containsExactly("first.png", "second.jpg");
    }

    @Test
    void inaccessibleReporterCannotIssueEvidenceUpload() {
        PresignUploadBatchRequest request = new PresignUploadBatchRequest(List.of(
                new PresignUploadBatchRequest.Item("image/png", 100L, "evidence.png")
        ));
        doThrow(new CustomException(AuthErrorCode.USER_WITHDRAWN))
                .when(accountAccessGuard).requireAccessibleForUpdate(1L);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.presignEvidenceBatch(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_WITHDRAWN);
        verifyNoInteractions(presignEngine, ticketRepository, evidenceRepository, globalPresignMethods);
    }

    @Test
    void consumesEvidenceKeysInOrderAndPersistsTheirMetadata() {
        Report report = new Report(
                null,
                1L,
                2L,
                null,
                TargetType.USER,
                ReportCategory.OTHER,
                "title",
                "context"
        );
        ReflectionTestUtils.setField(report, "reportId", 101L);
        UploadTicket first = ticket("temp/first.png", "first.png", "image/png", 100L);
        UploadTicket second = ticket("temp/second.jpg", "second.jpg", "image/jpeg", 200L);
        when(ticketRepository.findAllByStorageKeyInForUpdate(List.of("temp/first.png", "temp/second.jpg")))
                .thenReturn(List.of(first, second));
        when(presignEngine.consume(
                eq(1L), eq(UploadPurpose.REPORT_EVIDENCE), eq(UploadRefType.REPORT), eq(101L),
                eq("temp/first.png"), anyString()
        )).thenReturn("final/first.png");
        when(presignEngine.consume(
                eq(1L), eq(UploadPurpose.REPORT_EVIDENCE), eq(UploadRefType.REPORT), eq(101L),
                eq("temp/second.jpg"), anyString()
        )).thenReturn("final/second.jpg");
        when(evidenceRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ReportEvidence> result = service.applyOnReportCreate(
                1L,
                report,
                List.of("temp/second.jpg", "temp/first.png")
        );

        assertThat(result).extracting(ReportEvidence::getStorageKey)
                .containsExactly("final/second.jpg", "final/first.png");
        assertThat(result).extracting(ReportEvidence::getSortOrder).containsExactly(0, 1);
        assertThat(result).extracting(ReportEvidence::getOriginalFilename)
                .containsExactly("second.jpg", "first.png");

        InOrder order = inOrder(ticketRepository, presignEngine);
        order.verify(ticketRepository).findAllByStorageKeyInForUpdate(
                List.of("temp/first.png", "temp/second.jpg")
        );
        order.verify(presignEngine).consume(
                1L,
                UploadPurpose.REPORT_EVIDENCE,
                UploadRefType.REPORT,
                101L,
                "temp/second.jpg",
                "reports/user-1/report-101/evidence"
        );
    }

    private static UploadTicket ticket(String key, String filename, String contentType, long size) {
        return UploadTicket.builder()
                .userId(1L)
                .purpose(UploadPurpose.REPORT_EVIDENCE)
                .status(UploadTicket.Status.PENDING)
                .storageKey(key)
                .originalFilename(filename)
                .contentType(contentType)
                .size(size)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }
}
