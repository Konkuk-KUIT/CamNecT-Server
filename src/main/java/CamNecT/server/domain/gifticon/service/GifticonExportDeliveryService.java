package CamNecT.server.domain.gifticon.service;

import CamNecT.server.domain.gifticon.model.GifticonExportBatch;
import CamNecT.server.domain.gifticon.model.GifticonExportDeliveryStatus;
import CamNecT.server.domain.gifticon.model.GifticonPurchase;
import CamNecT.server.domain.gifticon.repository.GifticonExportBatchRepository;
import CamNecT.server.domain.gifticon.repository.GifticonPurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GifticonExportDeliveryService {

    static final int DUE_BATCH_LIMIT = 20;
    static final int MAX_DELIVERY_ATTEMPTS = 5;
    static final Duration RETRY_DELAY = Duration.ofHours(1);

    private final GifticonExportBatchRepository batchRepository;
    private final GifticonPurchaseRepository purchaseRepository;
    private final GifticonExportFileService fileService;
    private final GifticonExportMailService mailService;
    private final Clock clock;

    @Value("${app.gifticon.export-mail.delete-after-send:true}")
    private boolean deleteAfterSend;

    @Transactional(readOnly = true)
    public List<Long> findDueReadyBatchIds() {
        return batchRepository.findDueReadyBatchIds(
                GifticonExportDeliveryStatus.READY,
                LocalDateTime.now(clock),
                PageRequest.of(0, DUE_BATCH_LIMIT)
        );
    }

    @Transactional
    public DeliveryOutcome deliverBatch(Long batchId) {
        LocalDateTime now = LocalDateTime.now(clock);
        GifticonExportBatch batch = batchRepository.findByIdForDelivery(batchId).orElse(null);
        if (batch == null || !batch.isDue(now)) {
            return DeliveryOutcome.SKIPPED;
        }

        List<GifticonPurchase> purchases = purchaseRepository.findAllForExportBatch(batchId);
        if (purchases.isEmpty()) {
            return recordFailure(batch, now, "BATCH_PURCHASES_EMPTY");
        }
        if (batch.getItemCount() == null || batch.getItemCount() != purchases.size()) {
            return recordFailure(
                    batch,
                    now,
                    "BATCH_ITEM_COUNT_MISMATCH: expected=" + batch.getItemCount()
                            + ", actual=" + purchases.size()
            );
        }

        Path filePath;
        try {
            filePath = Path.of(batch.getFilePath());
            fileService.ensureFile(filePath, purchases);
        } catch (Exception e) {
            log.error("[gifticon-export] file generation failed batchId={}", batchId, e);
            return recordFailure(batch, now, describe("FILE_ERROR", e));
        }

        GifticonExportMailResult mailResult;
        try {
            mailResult = mailService.sendExportExcel(batch);
        } catch (RuntimeException e) {
            log.error("[gifticon-export] unexpected mail failure batchId={}", batchId, e);
            return recordFailure(batch, now, describe("MAIL_ERROR", e));
        }

        if (!mailResult.successful()) {
            return recordFailure(batch, now, mailResult.error());
        }

        batch.markSubmitted(now);
        if (deleteAfterSend) {
            fileService.deleteAfterCommit(filePath);
        }
        return DeliveryOutcome.SUBMITTED;
    }

    private DeliveryOutcome recordFailure(
            GifticonExportBatch batch,
            LocalDateTime attemptedAt,
            String error
    ) {
        batch.markDeliveryFailed(
                attemptedAt,
                error,
                MAX_DELIVERY_ATTEMPTS,
                attemptedAt.plus(RETRY_DELAY)
        );
        return batch.getDeliveryStatus() == GifticonExportDeliveryStatus.FAILED
                ? DeliveryOutcome.FAILED
                : DeliveryOutcome.RETRY_SCHEDULED;
    }

    private String describe(String prefix, Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return prefix + ": " + exception.getClass().getSimpleName();
        }
        return prefix + ": " + exception.getClass().getSimpleName() + ": " + message;
    }

    public enum DeliveryOutcome {
        SUBMITTED,
        RETRY_SCHEDULED,
        FAILED,
        SKIPPED
    }
}
