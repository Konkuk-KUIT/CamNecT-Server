package CamNecT.server.domain.verification.document.service;

import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.verification.document.config.DocumentVerificationProperties;
import CamNecT.server.domain.verification.document.model.DocumentType;
import CamNecT.server.domain.verification.document.repository.DocumentVerificationSubmissionRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.VerificationErrorCode;
import CamNecT.server.global.storage.dto.request.PresignUploadRequest;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.service.PresignEngine;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentVerificationStateLockingTest {

    private final DocumentVerificationSubmissionRepository submissionRepository =
            mock(DocumentVerificationSubmissionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PresignEngine presignEngine = mock(PresignEngine.class);
    private final UploadTicketRepository ticketRepository = mock(UploadTicketRepository.class);
    private final DocumentVerificationService service = new DocumentVerificationService(
            mock(DocumentVerificationProperties.class),
            submissionRepository,
            userRepository,
            presignEngine,
            ticketRepository,
            mock(GlobalPresignMethods.class)
    );

    @Test
    void presignRejectsActiveUserAfterLockingCurrentState() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(
                Users.builder().userId(1L).status(UserStatus.ACTIVE).build()
        ));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.presignUpload(1L,
                        new PresignUploadRequest("application/pdf", 100L, "proof.pdf")));

        assertThat(exception.getErrorCode())
                .isEqualTo(VerificationErrorCode.DOCUMENT_SUBMISSION_NOT_ALLOWED);
        verify(userRepository).findByIdForUpdate(1L);
        verifyNoInteractions(presignEngine);
    }

    @Test
    void submitRejectsUserWithdrawnWhileWaitingForTheLock() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(
                Users.builder().userId(1L).status(UserStatus.WITHDRAWN).build()
        ));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.submit(1L, DocumentType.ENROLLMENT_CERTIFICATE, "temp/key"));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_WITHDRAWN);
        verify(userRepository).findByIdForUpdate(1L);
        verifyNoInteractions(submissionRepository, ticketRepository, presignEngine);
    }
}
