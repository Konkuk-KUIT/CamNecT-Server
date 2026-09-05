package CamNecT.server.domain.gifticon.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GifticonExportFileServiceTest {

    @TempDir Path tempDir;

    private final GifticonExportFileService service = new GifticonExportFileService(mock(GifticonEmailPolicy.class));

    @Test
    void writesThroughTemporaryFileAndMovesToFinalPath() throws Exception {
        Path finalPath = tempDir.resolve("batch.xlsx");

        service.writeAtomically(finalPath, List.of());

        assertThat(finalPath).isRegularFile();
        try (var files = Files.list(tempDir)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactly("batch.xlsx");
        }
    }

    @Test
    void deleteAfterSendWaitsForTransactionCommit() throws Exception {
        Path file = Files.writeString(tempDir.resolve("batch.xlsx"), "content");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteAfterCommit(file);

            assertThat(file).exists();
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            synchronizations.forEach(TransactionSynchronization::afterCommit);
            assertThat(file).doesNotExist();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
