package CamNecT.server.global.storage.service;

import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.StorageErrorCode;
import CamNecT.server.global.storage.config.PresignProps;
import CamNecT.server.global.storage.config.S3Props;
import CamNecT.server.global.storage.model.UploadPurpose;
import CamNecT.server.global.storage.model.UploadRefType;
import CamNecT.server.global.storage.model.UploadTicket;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresignEngineConsumeLockingTest {

    private static final String TEMP_KEY = "temp/reports/evidence.jpg";

    @Mock S3Presigner presigner;
    @Mock S3Client s3;
    @Mock UploadTicketRepository ticketRepository;

    private PresignEngine presignEngine;

    @BeforeEach
    void setUp() {
        presignEngine = new PresignEngine(
                presigner,
                s3,
                new S3Props("bucket", "ap-northeast-2", "camnect"),
                new PresignProps(600, 600),
                ticketRepository,
                Clock.systemDefaultZone()
        );
    }

    @Test
    void locksTicketBeforeRejectingReuseByAnotherUser() {
        when(ticketRepository.findByStorageKeyForUpdate(TEMP_KEY))
                .thenReturn(Optional.of(ticket(2L, UploadPurpose.REPORT_EVIDENCE, UploadTicket.Status.PENDING)));

        assertConsumeFailsWith(StorageErrorCode.UPLOAD_TICKET_FORBIDDEN, 1L, UploadPurpose.REPORT_EVIDENCE, 10L);

        verify(ticketRepository).findByStorageKeyForUpdate(TEMP_KEY);
        verifyNoInteractions(s3);
    }

    @Test
    void locksTicketBeforeRejectingReuseForAnotherPurpose() {
        when(ticketRepository.findByStorageKeyForUpdate(TEMP_KEY))
                .thenReturn(Optional.of(ticket(1L, UploadPurpose.PROFILE_IMAGE, UploadTicket.Status.PENDING)));

        assertConsumeFailsWith(StorageErrorCode.UPLOAD_TICKET_FORBIDDEN, 1L, UploadPurpose.REPORT_EVIDENCE, 10L);

        verify(ticketRepository).findByStorageKeyForUpdate(TEMP_KEY);
        verifyNoInteractions(s3);
    }

    @Test
    void locksTicketBeforeRejectingReuseForAnotherReference() {
        when(ticketRepository.findByStorageKeyForUpdate(TEMP_KEY))
                .thenReturn(Optional.of(ticket(1L, UploadPurpose.REPORT_EVIDENCE, UploadTicket.Status.USED)));

        assertConsumeFailsWith(StorageErrorCode.UPLOAD_TICKET_EXPIRED_OR_USED, 1L, UploadPurpose.REPORT_EVIDENCE, 20L);

        verify(ticketRepository).findByStorageKeyForUpdate(TEMP_KEY);
        verifyNoInteractions(s3);
    }

    private void assertConsumeFailsWith(
            StorageErrorCode expected,
            Long userId,
            UploadPurpose purpose,
            Long refId
    ) {
        assertThatThrownBy(() -> presignEngine.consume(
                userId,
                purpose,
                UploadRefType.REPORT,
                refId,
                TEMP_KEY,
                "reports/final"
        )).isInstanceOfSatisfying(CustomException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(expected)
        );
    }

    private static UploadTicket ticket(
            Long userId,
            UploadPurpose purpose,
            UploadTicket.Status status
    ) {
        return UploadTicket.builder()
                .userId(userId)
                .purpose(purpose)
                .status(status)
                .storageKey(TEMP_KEY)
                .originalFilename("evidence.jpg")
                .contentType("image/jpeg")
                .size(100L)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }
}
