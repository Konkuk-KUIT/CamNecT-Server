package CamNecT.server.domain.gifticon.service;

import CamNecT.server.domain.gifticon.model.GifticonPurchase;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Slf4j
@Service
public class GifticonExportFileService {

    public void ensureFile(Path filePath, List<GifticonPurchase> rows) throws IOException {
        if (Files.isRegularFile(filePath)) return;
        writeAtomically(filePath, rows);
    }

    public void writeAtomically(Path filePath, List<GifticonPurchase> rows) throws IOException {
        Path normalizedPath = filePath.toAbsolutePath().normalize();
        Path parent = normalizedPath.getParent();
        if (parent == null) {
            throw new IOException("Export file must have a parent directory");
        }

        Files.createDirectories(parent);
        Path tempPath = Files.createTempFile(parent, normalizedPath.getFileName() + ".", ".tmp");
        try {
            writeXlsx(tempPath, rows);
            moveToFinalPath(tempPath, normalizedPath);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    public void deleteAfterCommit(Path filePath) {
        Path normalizedPath = filePath.toAbsolutePath().normalize();
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("[gifticon-export] skip delete without active transaction path={}", normalizedPath);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    Files.deleteIfExists(normalizedPath);
                    log.info("[gifticon-export] deleted submitted file path={}", normalizedPath);
                } catch (IOException e) {
                    log.warn("[gifticon-export] delete failed path={}", normalizedPath, e);
                }
            }
        });
    }

    private void moveToFinalPath(Path tempPath, Path filePath) throws IOException {
        try {
            Files.move(
                    tempPath,
                    filePath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeXlsx(Path filePath, List<GifticonPurchase> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("purchases");

            int rowIndex = 0;
            Row header = sheet.createRow(rowIndex++);
            String[] columns = {
                    "purchaseId", "requestedAt",
                    "userId", "buyerName", "buyerPhone", "buyerEmail",
                    "productId", "vendorProductCode", "brandName", "productName",
                    "unitPricePoints", "quantity", "totalPricePoints",
                    "recipientName", "recipientPhone", "giftMessage"
            };

            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            for (GifticonPurchase purchase : rows) {
                Row row = sheet.createRow(rowIndex++);
                int columnIndex = 0;

                columnIndex = writeCell(row, columnIndex, purchase.getId());
                columnIndex = writeCell(row, columnIndex, purchase.getRequestedAt());
                columnIndex = writeCell(row, columnIndex, purchase.getUser().getUserId());
                columnIndex = writeCell(row, columnIndex, purchase.getBuyerName());
                columnIndex = writeCell(row, columnIndex, purchase.getBuyerPhone());
                columnIndex = writeCell(row, columnIndex, purchase.getBuyerEmail());
                columnIndex = writeCell(row, columnIndex, purchase.getProduct().getId());
                columnIndex = writeCell(row, columnIndex, purchase.getProduct().getVendorProductCode());
                columnIndex = writeCell(row, columnIndex, purchase.getProduct().getBrandName());
                columnIndex = writeCell(row, columnIndex, purchase.getProduct().getProductName());
                columnIndex = writeCell(row, columnIndex, purchase.getUnitPricePoints());
                columnIndex = writeCell(row, columnIndex, purchase.getQuantity());
                columnIndex = writeCell(row, columnIndex, purchase.getTotalPricePoints());
                columnIndex = writeCell(row, columnIndex, purchase.getRecipientName());
                columnIndex = writeCell(row, columnIndex, purchase.getRecipientPhone());
                writeCell(row, columnIndex, purchase.getGiftMessage());
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (OutputStream output = Files.newOutputStream(filePath)) {
                workbook.write(output);
            }
        }
    }

    private int writeCell(Row row, int columnIndex, Object value) {
        row.createCell(columnIndex).setCellValue(value == null ? "" : String.valueOf(value));
        return columnIndex + 1;
    }
}
