package CamNecT.server.domain.verification.document.service;

import CamNecT.server.domain.profile.components.institutions.repository.InstitutionRepository;
import CamNecT.server.domain.profile.components.majors.repository.MajorRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.verification.document.dto.AdminReviewDocumentVerificationRequest;
import CamNecT.server.domain.verification.document.model.DocumentVerificationSubmission;
import CamNecT.server.domain.verification.document.model.VerificationStatus;
import CamNecT.server.domain.verification.document.repository.DocumentVerificationSubmissionRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.VerificationErrorCode;
import CamNecT.server.global.point.service.PointService;
import CamNecT.server.global.storage.service.PresignEngine;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDocumentVerificationLockingTest {

    private final DocumentVerificationSubmissionRepository submissionRepository =
            mock(DocumentVerificationSubmissionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminDocumentVerificationService service = new AdminDocumentVerificationService(
            submissionRepository,
            userRepository,
            mock(UserProfileRepository.class),
            mock(InstitutionRepository.class),
            mock(MajorRepository.class),
            mock(PresignEngine.class),
            mock(ApplicationEventPublisher.class),
            mock(PointService.class)
    );

    @Test
    void approvalRechecksUserStateUnderLock() {
        DocumentVerificationSubmission submission = DocumentVerificationSubmission.builder()
                .id(10L)
                .userId(1L)
                .status(VerificationStatus.PENDING)
                .build();
        Users withdrawn = Users.builder().userId(1L).status(UserStatus.WITHDRAWN).build();
        when(submissionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(submission));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(withdrawn));
        AdminReviewDocumentVerificationRequest request =
                new AdminReviewDocumentVerificationRequest(
                        AdminReviewDocumentVerificationRequest.Decision.APPROVE,
                        null, null, null, null, null);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.review(99L, 10L, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(VerificationErrorCode.ONLY_PENDING_CAN_REVIEW);
        assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        verify(userRepository).findByIdForUpdate(1L);
    }

    @Test
    void rejectionCanCloseAnOldPendingSubmissionWithoutReactivatingUser() {
        DocumentVerificationSubmission submission = DocumentVerificationSubmission.builder()
                .id(10L)
                .userId(1L)
                .status(VerificationStatus.PENDING)
                .build();
        Users withdrawn = Users.builder().userId(1L).status(UserStatus.WITHDRAWN).build();
        when(submissionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(submission));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(withdrawn));
        AdminReviewDocumentVerificationRequest request =
                new AdminReviewDocumentVerificationRequest(
                        AdminReviewDocumentVerificationRequest.Decision.REJECT,
                        "정리", null, null, null, null);

        service.review(99L, 10L, request);

        assertThat(submission.getStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
    }
}
