package CamNecT.server.domain.gifticon.service;

import CamNecT.server.domain.gifticon.model.GifticonExportBatch;
import CamNecT.server.domain.gifticon.model.GifticonExportDeliveryStatus;
import CamNecT.server.domain.gifticon.model.GifticonPurchase;
import CamNecT.server.domain.gifticon.repository.GifticonExportBatchRepository;
import CamNecT.server.domain.gifticon.repository.GifticonPurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GifticonExportServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-26T01:02:03Z"),
            ZoneOffset.UTC
    );

    @Mock GifticonPurchaseRepository purchaseRepository;
    @Mock GifticonExportBatchRepository batchRepository;
    @Mock GifticonPurchase firstPurchase;
    @Mock GifticonPurchase secondPurchase;

    @TempDir Path exportDir;

    private GifticonExportService service;

    @BeforeEach
    void setUp() {
        service = new GifticonExportService(
                purchaseRepository,
                batchRepository,
                CLOCK
        );
        ReflectionTestUtils.setField(service, "exportDir", exportDir.toString());
    }

    @Test
    void preparesAtMostFiveHundredLockedRowsWithStableUuidPath() throws Exception {
        List<GifticonPurchase> purchases = List.of(firstPurchase, secondPurchase);
        when(purchaseRepository.findNextUnbatchedForExport(any(Pageable.class)))
                .thenReturn(purchases);
        when(batchRepository.save(any(GifticonExportBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GifticonExportBatch batch = service.exportRequestedPurchasesToXlsx();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(purchaseRepository).findNextUnbatchedForExport(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(500);

        assertThat(batch.getFileName()).matches(
                "gifticon_purchases_20260826_010203_"
                        + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.xlsx"
        );
        assertThat(Path.of(batch.getFilePath())).isEqualTo(
                exportDir.resolve(batch.getFileName()).toAbsolutePath().normalize()
        );
        assertThat(batch.getDeliveryStatus()).isEqualTo(GifticonExportDeliveryStatus.READY);
        assertThat(batch.getDeliveryAttemptCount()).isZero();
        assertThat(batch.getNextAttemptAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(batch.getItemCount()).isEqualTo(2);

        verify(firstPurchase).markExported(batch, LocalDateTime.now(CLOCK));
        verify(secondPurchase).markExported(batch, LocalDateTime.now(CLOCK));
    }
}
