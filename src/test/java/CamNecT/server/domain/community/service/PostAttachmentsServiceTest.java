package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.dto.request.AttachmentRequest;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.props.CommunityAttachmentProps;
import CamNecT.server.domain.community.repository.Posts.PostAttachmentsRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.StorageErrorCode;
import CamNecT.server.global.storage.dto.request.PresignUploadBatchRequest;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.service.PresignEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostAttachmentsServiceTest {

    @Mock PostAttachmentsRepository postAttachmentsRepository;
    @Mock AccountAccessGuard accountAccessGuard;
    @Mock PresignEngine presignEngine;
    @Mock CommunityAttachmentProps attachmentProps;
    @Mock GlobalPresignMethods globalPresignMethods;

    @InjectMocks PostAttachmentsService service;

    @BeforeEach
    void setUpLimits() {
        lenient().when(attachmentProps.maxFiles()).thenReturn(3);
        lenient().when(attachmentProps.maxFileSizeBytes()).thenReturn(10L * 1024 * 1024);
    }

    @Test
    void duplicateAttachmentKeysAreRejectedBeforeExistingFilesChange() {
        AttachmentRequest first = new AttachmentRequest("temp/file.png", 100, 100, 1000L);
        AttachmentRequest duplicate = new AttachmentRequest("temp/file.png", 200, 200, 1000L);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.replace(mock(Posts.class), 1L, List.of(first, duplicate)));

        assertThat(exception.getErrorCode()).isEqualTo(StorageErrorCode.DUPLICATE_ATTACHMENT_KEY);
        verifyNoInteractions(postAttachmentsRepository, presignEngine);
    }

    @Test
    void invalidAttachmentMetadataIsRejected() {
        AttachmentRequest invalid = new AttachmentRequest("temp/file.png", 100, null, 1000L);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.replace(mock(Posts.class), 1L, List.of(invalid)));

        assertThat(exception.getErrorCode()).isEqualTo(StorageErrorCode.INVALID_ATTACHMENT_METADATA);
        verifyNoInteractions(postAttachmentsRepository, presignEngine);
    }

    @Test
    void inaccessibleActorCannotIssueAttachmentUploadTicket() {
        doThrow(new CustomException(AuthErrorCode.USER_WITHDRAWN))
                .when(accountAccessGuard).requireAccessibleForUpdate(1L);
        PresignUploadBatchRequest request = new PresignUploadBatchRequest(List.of(
                new PresignUploadBatchRequest.Item("image/png", 100L, "community.png")
        ));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.presignAttachmentsBatch(1L, request));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_WITHDRAWN);
        verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        verifyNoInteractions(presignEngine, globalPresignMethods);
    }
}
