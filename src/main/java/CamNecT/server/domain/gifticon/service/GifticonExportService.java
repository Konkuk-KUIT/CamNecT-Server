package CamNecT.server.domain.gifticon.service;

import CamNecT.server.domain.gifticon.model.GifticonExportBatch;
import CamNecT.server.domain.gifticon.model.GifticonPurchase;
import CamNecT.server.domain.gifticon.repository.GifticonExportBatchRepository;
import CamNecT.server.domain.gifticon.repository.GifticonPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GifticonExportService {

    static final int MAX_BATCH_SIZE = 500;

    private final GifticonPurchaseRepository purchaseRepository;
    private final GifticonExportBatchRepository batchRepository;
    private final Clock clock;

    @Value("${app.gifticon.export-dir:/tmp/gifticon-exports}")
    private String exportDir;

    @Transactional
    public GifticonExportBatch exportRequestedPurchasesToXlsx() {
        List<GifticonPurchase> targets = purchaseRepository.findNextUnbatchedForExport(
                PageRequest.of(0, MAX_BATCH_SIZE)
        );
        if (targets.isEmpty()) return null;

        LocalDateTime now = LocalDateTime.now(clock);
        String ts = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "gifticon_purchases_" + ts + "_" + UUID.randomUUID() + ".xlsx";
        Path filePath = Path.of(exportDir, fileName).toAbsolutePath().normalize();

        GifticonExportBatch batch = batchRepository.save(GifticonExportBatch.builder()
                .exportedAt(now)
                .filePath(filePath.toString())
                .fileName(fileName)
                .itemCount(targets.size())
                .nextAttemptAt(now)
                .build());

        for (GifticonPurchase purchase : targets) {
            purchase.markExported(batch, now);
        }

        return batch;
    }
}
