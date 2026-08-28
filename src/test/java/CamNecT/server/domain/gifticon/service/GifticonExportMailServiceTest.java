package CamNecT.server.domain.gifticon.service;

import CamNecT.server.domain.gifticon.model.GifticonExportBatch;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GifticonExportMailServiceTest {

    @Mock JavaMailSender mailSender;
    @TempDir Path tempDir;

    private GifticonExportMailService service;

    @BeforeEach
    void setUp() {
        service = new GifticonExportMailService(mailSender);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "to", "ops@example.com");
        ReflectionTestUtils.setField(service, "from", "sender@example.com");
        ReflectionTestUtils.setField(service, "subject", "Gifticon export");
    }

    @Test
    void disabledMailIsExplicitFailure() {
        ReflectionTestUtils.setField(service, "enabled", false);

        GifticonExportMailResult result = service.sendExportExcel(batch(tempDir.resolve("missing.xlsx")));

        assertThat(result.successful()).isFalse();
        assertThat(result.error()).isEqualTo("MAIL_DISABLED");
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void emptyRecipientIsExplicitFailure() {
        ReflectionTestUtils.setField(service, "to", "  ");

        GifticonExportMailResult result = service.sendExportExcel(batch(tempDir.resolve("missing.xlsx")));

        assertThat(result.successful()).isFalse();
        assertThat(result.error()).isEqualTo("RECIPIENT_EMPTY");
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void missingFileIsExplicitFailure() {
        GifticonExportMailResult result = service.sendExportExcel(batch(tempDir.resolve("missing.xlsx")));

        assertThat(result.successful()).isFalse();
        assertThat(result.error()).startsWith("FILE_MISSING:");
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void successfulSubmissionIncludesBatchIdInSubjectAndBody() throws Exception {
        Path file = Files.write(tempDir.resolve("batch.xlsx"), new byte[]{1, 2, 3});
        GifticonExportBatch batch = batch(file);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        GifticonExportMailResult result = service.sendExportExcel(batch);

        assertThat(result.successful()).isTrue();
        assertThat(message.getSubject()).contains("batchId=912");
        assertThat(extractText(message)).contains("Batch ID: <b>912</b>");
        verify(mailSender).send(message);
    }

    @Test
    void smtpExceptionIsExplicitFailure() throws Exception {
        Path file = Files.write(tempDir.resolve("batch.xlsx"), new byte[]{1, 2, 3});
        GifticonExportBatch batch = batch(file);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(message);

        GifticonExportMailResult result = service.sendExportExcel(batch);

        assertThat(result.successful()).isFalse();
        assertThat(result.error()).contains("MailSendException", "smtp down");
    }

    private GifticonExportBatch batch(Path file) {
        return GifticonExportBatch.builder()
                .id(912L)
                .exportedAt(LocalDateTime.of(2026, 8, 26, 13, 0))
                .filePath(file.toString())
                .fileName(file.getFileName().toString())
                .itemCount(3)
                .build();
    }

    private String extractText(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                result.append(extractText(multipart.getBodyPart(i)));
            }
            return result.toString();
        }
        return "";
    }
}
