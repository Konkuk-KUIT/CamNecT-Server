package CamNecT.server.domain.gifticon.service;

import CamNecT.server.domain.gifticon.model.GifticonProduct;
import CamNecT.server.domain.gifticon.model.GifticonPurchase;
import CamNecT.server.domain.users.model.Users;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class GifticonEmailExportFileServiceTest {

    @TempDir Path exportDir;
    private ValidatorFactory validatorFactory;
    private GifticonExportFileService service;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        service = new GifticonExportFileService(new GifticonEmailPolicy(validatorFactory.getValidator()));
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void exportsEmailRecipientsAndFlagsUnresolvedLegacyOrdersWithoutFallback() throws Exception {
        var filePath = exportDir.resolve("batch.xlsx");
        service.writeAtomically(filePath, List.of(purchase(1L, "buyer@example.com"),
                purchase(2L, "recipient@example.com"), purchase(3L, null), purchase(4L, "invalid")));

        try (var workbook = WorkbookFactory.create(filePath.toFile())) {
            var sheet = workbook.getSheet("purchases");
            var header = sheet.getRow(0);
            var columns = IntStream.range(0, header.getLastCellNum())
                    .mapToObj(i -> header.getCell(i).getStringCellValue()).toList();
            assertThat(columns).containsExactly("purchaseId", "requestedAt", "userId", "buyerName",
                    "buyerEmail", "productId", "vendorProductCode", "brandName", "productName",
                    "unitPricePoints", "quantity", "totalPricePoints", "recipientName", "recipientEmail",
                    "deliveryStatus", "giftMessage");
            assertThat(sheet.getLastRowNum()).isEqualTo(4);
            assertThat(sheet.getRow(1).getCell(13).getStringCellValue()).isEqualTo("buyer@example.com");
            assertThat(sheet.getRow(2).getCell(13).getStringCellValue()).isEqualTo("recipient@example.com");
            assertThat(sheet.getRow(2).getCell(4).getStringCellValue()).isEqualTo("buyer@example.com");
            assertThat(sheet.getRow(2).getCell(14).getStringCellValue()).isEqualTo("READY");
            assertThat(sheet.getRow(3).getCell(13).getStringCellValue()).isEmpty();
            assertThat(sheet.getRow(3).getCell(14).getStringCellValue()).isEqualTo("EMAIL_REQUIRED");
            assertThat(sheet.getRow(4).getCell(14).getStringCellValue()).isEqualTo("EMAIL_REQUIRED");
            assertThat(sheet.getRow(2).getCell(15).getStringCellValue()).isEqualTo("메시지");
        }
    }

    @Test
    void retryRebuildsCachedPhoneSpreadsheetUsingMigratedEmailSnapshot() throws Exception {
        var filePath = exportDir.resolve("legacy.xlsx");
        try (var workbook = new XSSFWorkbook(); var output = Files.newOutputStream(filePath)) {
            var sheet = workbook.createSheet("purchases");
            sheet.createRow(0).createCell(0).setCellValue("recipientPhone");
            sheet.createRow(1).createCell(0).setCellValue("01012345678");
            workbook.write(output);
        }

        service.ensureFile(filePath, List.of(purchase(1L, "recipient@example.com")));

        try (var workbook = WorkbookFactory.create(filePath.toFile())) {
            var sheet = workbook.getSheet("purchases");
            assertThat(sheet.getRow(0).getCell(13).getStringCellValue()).isEqualTo("recipientEmail");
            assertThat(sheet.getRow(1).getCell(13).getStringCellValue()).isEqualTo("recipient@example.com");
            for (var row : sheet) {
                for (var cell : row) {
                    assertThat(cell.getStringCellValue()).doesNotContain("Phone", "01012345678");
                }
            }
        }
    }

    @Test
    void retryKeepsAnExistingEmailSpreadsheet() throws Exception {
        var filePath = exportDir.resolve("email.xlsx");
        service.writeAtomically(filePath, List.of(purchase(1L, "recipient@example.com")));
        byte[] original = Files.readAllBytes(filePath);

        service.ensureFile(filePath, List.of());

        assertThat(Files.readAllBytes(filePath)).isEqualTo(original);
    }

    @Test
    void retryRebuildsAnUnreadableSpreadsheet() throws Exception {
        var filePath = Files.writeString(exportDir.resolve("corrupt.xlsx"), "incomplete workbook");

        service.ensureFile(filePath, List.of(purchase(1L, "recipient@example.com")));

        try (var workbook = WorkbookFactory.create(filePath.toFile())) {
            assertThat(workbook.getSheet("purchases").getRow(1).getCell(13).getStringCellValue())
                    .isEqualTo("recipient@example.com");
        }
    }

    private GifticonPurchase purchase(long id, String recipientEmail) {
        return GifticonPurchase.builder().id(id)
                .user(Users.builder().userId(1L).email("current@example.com").build())
                .product(GifticonProduct.builder().id(10L).vendorProductCode("vendor-10")
                        .brandName("브랜드").productName("상품").build())
                .buyerName("구매자").buyerEmail("buyer@example.com")
                .recipientName("수신자").recipientEmail(recipientEmail).giftMessage("메시지")
                .quantity(2).unitPricePoints(1000).totalPricePoints(2000)
                .requestedAt(LocalDateTime.of(2026, 9, 5, 12, 0)).build();
    }
}
