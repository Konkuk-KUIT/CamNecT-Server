package CamNecT.server.domain.gifticon.service;

import CamNecT.server.domain.gifticon.model.GifticonExportBatch;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class GifticonExportMailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Value("${app.gifticon.export-mail.enabled:true}")
    private boolean enabled;

    @Value("${app.gifticon.export-mail.to:camnect.official@gmail.com}")
    private String to;

    @Value("${app.gifticon.export-mail.subject:[CamNecT] 기프티콘 구매요청 엑셀}")
    private String subject;

    public GifticonExportMailResult sendExportExcel(GifticonExportBatch batch) {
        if (!enabled) {
            return GifticonExportMailResult.failed("MAIL_DISABLED");
        }

        if (!StringUtils.hasText(to)) {
            return GifticonExportMailResult.failed("RECIPIENT_EMPTY");
        }

        File file = new File(batch.getFilePath());
        if (!file.isFile()) {
            return GifticonExportMailResult.failed("FILE_MISSING: " + batch.getFilePath());
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    "UTF-8"
            );

            helper.setTo(to);

            if (StringUtils.hasText(from)) {
                helper.setFrom(from);
            }

            String exportedAt = batch.getExportedAt()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            helper.setSubject(subject + " [batchId=" + batch.getId() + "] (" + exportedAt + ")");

            String text = """
                    기프티콘 구매요청 엑셀 파일을 전달드립니다.

                    - Export 시각: %s
                    - Batch ID: %d
                    - 건수: %d
                    - 파일명: %s
                    """.formatted(exportedAt, batch.getId(), batch.getItemCount(), batch.getFileName());

            String html = """
                    <div style="font-family: Arial, sans-serif; line-height: 1.6;">
                      <h2>기프티콘 구매요청 엑셀</h2>
                      <ul>
                        <li>Export 시각: <b>%s</b></li>
                        <li>Batch ID: <b>%d</b></li>
                        <li>건수: <b>%d</b></li>
                        <li>파일명: <b>%s</b></li>
                      </ul>
                      <p>첨부파일을 확인해 주세요.</p>
                    </div>
                    """.formatted(exportedAt, batch.getId(), batch.getItemCount(), batch.getFileName());

            helper.setText(text, html);

            helper.addAttachment(batch.getFileName(), new FileSystemResource(file));

            log.info("[gifticon-mail] send export excel to={} file={}", to, batch.getFileName());
            mailSender.send(mimeMessage);

            log.info("[gifticon-mail] submitted to={} batchId={} file={}",
                    to, batch.getId(), batch.getFileName());
            return GifticonExportMailResult.delivered();

        } catch (MessagingException | MailException e) {
            log.error("[gifticon-mail] submit failed to={} batchId={}", to, batch.getId(), e);
            return GifticonExportMailResult.failed(describe(e));
        }
    }

    private String describe(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }
}
