package CamNecT.server.domain.gifticon.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GifticonExportBatchTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 26, 10, 0);

    @Test
    void failedAttemptStaysReadyUntilMaximumAndSchedulesRetry() {
        GifticonExportBatch batch = readyBatch(0);

        batch.markDeliveryFailed(NOW, "smtp unavailable", 5, NOW.plusHours(1));

        assertThat(batch.getDeliveryStatus()).isEqualTo(GifticonExportDeliveryStatus.READY);
        assertThat(batch.getDeliveryAttemptCount()).isEqualTo(1);
        assertThat(batch.getLastAttemptAt()).isEqualTo(NOW);
        assertThat(batch.getNextAttemptAt()).isEqualTo(NOW.plusHours(1));
        assertThat(batch.getLastError()).isEqualTo("smtp unavailable");
        assertThat(batch.getSubmittedAt()).isNull();
    }

    @Test
    void fifthFailedAttemptBecomesTerminalAndTruncatesError() {
        GifticonExportBatch batch = readyBatch(4);
        String oversizedError = "x".repeat(GifticonExportBatch.LAST_ERROR_MAX_LENGTH + 50);

        batch.markDeliveryFailed(NOW, oversizedError, 5, NOW.plusHours(1));

        assertThat(batch.getDeliveryStatus()).isEqualTo(GifticonExportDeliveryStatus.FAILED);
        assertThat(batch.getDeliveryAttemptCount()).isEqualTo(5);
        assertThat(batch.getNextAttemptAt()).isNull();
        assertThat(batch.getLastError()).hasSize(GifticonExportBatch.LAST_ERROR_MAX_LENGTH);
    }

    @Test
    void submittedAttemptClearsRetryState() {
        GifticonExportBatch batch = GifticonExportBatch.builder()
                .deliveryStatus(GifticonExportDeliveryStatus.READY)
                .deliveryAttemptCount(2)
                .nextAttemptAt(NOW)
                .lastError("previous failure")
                .build();

        batch.markSubmitted(NOW);

        assertThat(batch.getDeliveryStatus()).isEqualTo(GifticonExportDeliveryStatus.SUBMITTED);
        assertThat(batch.getDeliveryAttemptCount()).isEqualTo(3);
        assertThat(batch.getLastAttemptAt()).isEqualTo(NOW);
        assertThat(batch.getSubmittedAt()).isEqualTo(NOW);
        assertThat(batch.getNextAttemptAt()).isNull();
        assertThat(batch.getLastError()).isNull();
    }

    private GifticonExportBatch readyBatch(int attempts) {
        return GifticonExportBatch.builder()
                .deliveryStatus(GifticonExportDeliveryStatus.READY)
                .deliveryAttemptCount(attempts)
                .nextAttemptAt(NOW)
                .build();
    }
}
