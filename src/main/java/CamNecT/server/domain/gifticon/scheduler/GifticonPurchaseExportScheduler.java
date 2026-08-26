package CamNecT.server.domain.gifticon.scheduler;

import CamNecT.server.domain.gifticon.model.GifticonExportBatch;
import CamNecT.server.domain.gifticon.service.GifticonExportDeliveryService;
import CamNecT.server.domain.gifticon.service.GifticonExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GifticonPurchaseExportScheduler {

    private final GifticonExportService exportService;
    private final GifticonExportDeliveryService deliveryService;

    @Scheduled(cron = "${app.gifticon.export-cron:0 10 3 * * *}")
    public void exportPurchases() {
        try {
            for (Long batchId : deliveryService.findDueReadyBatchIds()) {
                deliverSafely(batchId);
            }

            GifticonExportBatch batch = exportService.exportRequestedPurchasesToXlsx();
            if (batch != null) {
                log.info("[GifticonPurchaseExportScheduler] exported: {} ({})",
                        batch.getFileName(), batch.getItemCount());
                deliverSafely(batch.getId());
            } else {
                log.info("[GifticonPurchaseExportScheduler] no requested purchases");
            }
        } catch (Exception e) {
            log.error("[GifticonPurchaseExportScheduler] export failed", e);
        }
    }

    private void deliverSafely(Long batchId) {
        try {
            GifticonExportDeliveryService.DeliveryOutcome outcome = deliveryService.deliverBatch(batchId);
            log.info("[GifticonPurchaseExportScheduler] delivery batchId={} outcome={}", batchId, outcome);
        } catch (Exception e) {
            log.error("[GifticonPurchaseExportScheduler] delivery failed batchId={}", batchId, e);
        }
    }
}
