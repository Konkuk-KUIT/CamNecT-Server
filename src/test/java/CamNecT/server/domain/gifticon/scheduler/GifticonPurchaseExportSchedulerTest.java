package CamNecT.server.domain.gifticon.scheduler;

import CamNecT.server.domain.gifticon.model.GifticonExportBatch;
import CamNecT.server.domain.gifticon.service.GifticonExportDeliveryService;
import CamNecT.server.domain.gifticon.service.GifticonExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GifticonPurchaseExportSchedulerTest {

    @Mock GifticonExportService exportService;
    @Mock GifticonExportDeliveryService deliveryService;

    @Test
    void restartDeliversExistingReadyBatchesBeforePreparingAndDeliveringNewBatch() {
        GifticonExportBatch newBatch = GifticonExportBatch.builder()
                .id(30L)
                .fileName("batch-30.xlsx")
                .itemCount(1)
                .build();
        when(deliveryService.findDueReadyBatchIds()).thenReturn(List.of(10L, 20L));
        when(exportService.exportRequestedPurchasesToXlsx()).thenReturn(newBatch);

        new GifticonPurchaseExportScheduler(exportService, deliveryService).exportPurchases();

        InOrder order = inOrder(deliveryService, exportService);
        order.verify(deliveryService).findDueReadyBatchIds();
        order.verify(deliveryService).deliverBatch(10L);
        order.verify(deliveryService).deliverBatch(20L);
        order.verify(exportService).exportRequestedPurchasesToXlsx();
        order.verify(deliveryService).deliverBatch(30L);
    }

    @Test
    void restartStillDeliversReadyBatchWhenThereAreNoNewPurchases() {
        when(deliveryService.findDueReadyBatchIds()).thenReturn(List.of(10L));
        when(exportService.exportRequestedPurchasesToXlsx()).thenReturn(null);

        new GifticonPurchaseExportScheduler(exportService, deliveryService).exportPurchases();

        InOrder order = inOrder(deliveryService, exportService);
        order.verify(deliveryService).findDueReadyBatchIds();
        order.verify(deliveryService).deliverBatch(10L);
        order.verify(exportService).exportRequestedPurchasesToXlsx();
    }
}
