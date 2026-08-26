package CamNecT.server.domain.gifticon.repository;

import CamNecT.server.domain.gifticon.model.GifticonExportBatch;
import CamNecT.server.domain.gifticon.model.GifticonPurchase;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GifticonExportRepositoryContractTest {

    @Test
    void unbatchedSelectionLocksOnlyPurchasesInStableOrder() throws Exception {
        Method method = GifticonPurchaseRepository.class.getMethod(
                "findNextUnbatchedForExport",
                Pageable.class
        );

        assertThat(method.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);

        String query = normalize(method.getAnnotation(Query.class).value());
        assertThat(query).contains(
                "where p.exportBatch is null",
                "order by p.requestedAt asc, p.id asc"
        );
        assertThat(query).doesNotContain("join fetch");
    }

    @Test
    void batchRebuildFetchesUserAndProductWithoutExpandingTheClaimLock() throws Exception {
        Method method = GifticonPurchaseRepository.class.getMethod(
                "findAllForExportBatch",
                Long.class
        );

        assertThat(method.getAnnotation(Lock.class)).isNull();
        String query = normalize(method.getAnnotation(Query.class).value());
        assertThat(query).contains(
                "join fetch p.user",
                "join fetch p.product",
                "where p.exportBatch.id = :batchId",
                "order by p.requestedAt asc, p.id asc"
        );
    }

    @Test
    void deliveryLoadsBatchUnderWriteLock() throws Exception {
        Method method = GifticonExportBatchRepository.class.getMethod(
                "findByIdForDelivery",
                Long.class
        );

        assertThat(method.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void entitiesDeclareQueueAndDueIndexes() {
        var purchaseTable = GifticonPurchase.class.getAnnotation(jakarta.persistence.Table.class);
        assertThat(purchaseTable.indexes()).anySatisfy(index -> {
            assertThat(index.name()).isEqualTo("idx_gifticon_purchase_export_queue");
            assertThat(index.columnList()).isEqualTo("export_batch_id,requested_at,purchase_id");
        });
        assertThat(purchaseTable.indexes())
                .noneSatisfy(index -> assertThat(index.name())
                        .isEqualTo("idx_gifticon_purchase_export"));

        var batchTable = GifticonExportBatch.class.getAnnotation(jakarta.persistence.Table.class);
        assertThat(batchTable.indexes()).anySatisfy(index -> {
            assertThat(index.name()).isEqualTo("idx_gifticon_export_delivery_due");
            assertThat(index.columnList()).isEqualTo(
                    "delivery_status,next_attempt_at,export_batch_id"
            );
        });
    }

    private String normalize(String query) {
        return query.replaceAll("\\s+", " ").trim();
    }
}
