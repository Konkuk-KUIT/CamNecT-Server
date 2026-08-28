package CamNecT.server.domain.gifticon.service;

import CamNecT.server.domain.gifticon.model.GifticonExportBatch;
import CamNecT.server.domain.gifticon.model.GifticonExportDeliveryStatus;
import CamNecT.server.domain.gifticon.model.GifticonPurchase;
import CamNecT.server.domain.gifticon.repository.GifticonExportBatchRepository;
import CamNecT.server.domain.gifticon.repository.GifticonPurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GifticonExportDeliveryServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-26T04:00:00Z"),
            ZoneOffset.UTC
    );
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock GifticonExportBatchRepository batchRepository;
    @Mock GifticonPurchaseRepository purchaseRepository;
    @Mock GifticonExportFileService fileService;
    @Mock GifticonExportMailService mailService;
    @Mock GifticonPurchase purchase;

    private GifticonExportDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new GifticonExportDeliveryService(
                batchRepository,
                purchaseRepository,
                fileService,
                mailService,
                CLOCK
        );
        ReflectionTestUtils.setField(service, "deleteAfterSend", true);
    }

    @Test
    void successfulSubmissionTransitionsStateAndSchedulesPostCommitDeletion() throws Exception {
        GifticonExportBatch batch = readyBatch(0);
        stubLockedBatch(batch);
        when(mailService.sendExportExcel(batch)).thenReturn(GifticonExportMailResult.delivered());

        var outcome = service.deliverBatch(batch.getId());

        assertThat(outcome).isEqualTo(GifticonExportDeliveryService.DeliveryOutcome.SUBMITTED);
        assertThat(batch.getDeliveryStatus()).isEqualTo(GifticonExportDeliveryStatus.SUBMITTED);
        assertThat(batch.getDeliveryAttemptCount()).isEqualTo(1);
        assertThat(batch.getLastAttemptAt()).isEqualTo(NOW);
        assertThat(batch.getSubmittedAt()).isEqualTo(NOW);
        assertThat(batch.getNextAttemptAt()).isNull();
        verify(fileService).ensureFile(Path.of(batch.getFilePath()), List.of(purchase));
        verify(fileService).deleteAfterCommit(Path.of(batch.getFilePath()));
    }

    @Test
    void explicitMailFailureRemainsReadyForFixedDelayRetry() {
        GifticonExportBatch batch = readyBatch(0);
        stubLockedBatch(batch);
        when(mailService.sendExportExcel(batch))
                .thenReturn(GifticonExportMailResult.failed("MAIL_DISABLED"));

        var outcome = service.deliverBatch(batch.getId());

        assertThat(outcome).isEqualTo(
                GifticonExportDeliveryService.DeliveryOutcome.RETRY_SCHEDULED
        );
        assertThat(batch.getDeliveryStatus()).isEqualTo(GifticonExportDeliveryStatus.READY);
        assertThat(batch.getDeliveryAttemptCount()).isEqualTo(1);
        assertThat(batch.getNextAttemptAt()).isEqualTo(NOW.plusHours(1));
        assertThat(batch.getLastError()).isEqualTo("MAIL_DISABLED");
        verify(fileService, never()).deleteAfterCommit(any());
    }

    @Test
    void fifthMailFailureBecomesTerminal() {
        GifticonExportBatch batch = readyBatch(4);
        stubLockedBatch(batch);
        when(mailService.sendExportExcel(batch))
                .thenReturn(GifticonExportMailResult.failed("SMTP_ERROR"));

        var outcome = service.deliverBatch(batch.getId());

        assertThat(outcome).isEqualTo(GifticonExportDeliveryService.DeliveryOutcome.FAILED);
        assertThat(batch.getDeliveryStatus()).isEqualTo(GifticonExportDeliveryStatus.FAILED);
        assertThat(batch.getDeliveryAttemptCount()).isEqualTo(5);
        assertThat(batch.getNextAttemptAt()).isNull();
    }

    @Test
    void missingFileRegenerationFailureIsRecordedWithoutSendingMail() throws Exception {
        GifticonExportBatch batch = readyBatch(0);
        stubLockedBatch(batch);
        org.mockito.Mockito.doThrow(new IOException("disk full"))
                .when(fileService)
                .ensureFile(Path.of(batch.getFilePath()), List.of(purchase));

        var outcome = service.deliverBatch(batch.getId());

        assertThat(outcome).isEqualTo(
                GifticonExportDeliveryService.DeliveryOutcome.RETRY_SCHEDULED
        );
        assertThat(batch.getLastError()).contains("FILE_ERROR", "disk full");
        verify(mailService, never()).sendExportExcel(any());
    }

    @Test
    void dueLookupIsBounded() {
        when(batchRepository.findDueReadyBatchIds(
                any(GifticonExportDeliveryStatus.class),
                any(),
                any(Pageable.class)
        ))
                .thenReturn(List.of(3L, 4L));

        assertThat(service.findDueReadyBatchIds()).containsExactly(3L, 4L);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(batchRepository).findDueReadyBatchIds(
                org.mockito.ArgumentMatchers.eq(GifticonExportDeliveryStatus.READY),
                any(),
                pageable.capture()
        );
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    private void stubLockedBatch(GifticonExportBatch batch) {
        when(batchRepository.findByIdForDelivery(batch.getId())).thenReturn(Optional.of(batch));
        when(purchaseRepository.findAllForExportBatch(batch.getId())).thenReturn(List.of(purchase));
    }

    private GifticonExportBatch readyBatch(int attempts) {
        return GifticonExportBatch.builder()
                .id(77L)
                .exportedAt(NOW.minusMinutes(5))
                .filePath("build/test-gifticon/batch-77.xlsx")
                .fileName("batch-77.xlsx")
                .itemCount(1)
                .deliveryStatus(GifticonExportDeliveryStatus.READY)
                .deliveryAttemptCount(attempts)
                .nextAttemptAt(NOW)
                .build();
    }
}
