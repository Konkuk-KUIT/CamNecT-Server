package CamNecT.server.global.storage.repository;

import CamNecT.server.global.storage.model.UploadPurpose;
import CamNecT.server.global.storage.model.UploadTicket;
import CamNecT.server.global.common.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class UploadTicketRepositoryTest {

    @Autowired
    private UploadTicketRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void expiresOnlyPendingTicketsAtOrBeforeTheSharedCutoff() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 0);
        UploadTicket expired = saveTicket(1L, UploadPurpose.REPORT_EVIDENCE, "temp/expired.jpg", now.minusSeconds(1));
        UploadTicket boundary = saveTicket(1L, UploadPurpose.REPORT_EVIDENCE, "temp/boundary.jpg", now);
        UploadTicket active = saveTicket(1L, UploadPurpose.REPORT_EVIDENCE, "temp/active.jpg", now.plusSeconds(1));
        UploadTicket otherUser = saveTicket(2L, UploadPurpose.REPORT_EVIDENCE, "temp/other-user.jpg", now.minusSeconds(1));
        UploadTicket otherPurpose = saveTicket(1L, UploadPurpose.PROFILE_IMAGE, "temp/other-purpose.jpg", now.minusSeconds(1));

        repository.bulkExpirePendingByUserPurpose(1L, UploadPurpose.REPORT_EVIDENCE, now);
        entityManager.clear();

        assertThat(repository.findById(expired.getId()).orElseThrow().getStatus())
                .isEqualTo(UploadTicket.Status.EXPIRED);
        assertThat(repository.findById(boundary.getId()).orElseThrow().getStatus())
                .isEqualTo(UploadTicket.Status.EXPIRED);
        assertThat(repository.findById(active.getId()).orElseThrow().getStatus())
                .isEqualTo(UploadTicket.Status.PENDING);
        assertThat(repository.findById(otherUser.getId()).orElseThrow().getStatus())
                .isEqualTo(UploadTicket.Status.PENDING);
        assertThat(repository.findById(otherPurpose.getId()).orElseThrow().getStatus())
                .isEqualTo(UploadTicket.Status.PENDING);
    }

    private UploadTicket saveTicket(
            Long userId,
            UploadPurpose purpose,
            String storageKey,
            LocalDateTime expiresAt
    ) {
        return entityManager.persistAndFlush(UploadTicket.builder()
                .userId(userId)
                .purpose(purpose)
                .status(UploadTicket.Status.PENDING)
                .storageKey(storageKey)
                .originalFilename("evidence.jpg")
                .contentType("image/jpeg")
                .size(1L)
                .expiresAt(expiresAt)
                .build());
    }
}
