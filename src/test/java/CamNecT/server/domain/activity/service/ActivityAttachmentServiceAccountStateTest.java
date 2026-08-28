package CamNecT.server.domain.activity.service;

import CamNecT.server.domain.activity.model.props.ActivityAttachmentProps;
import CamNecT.server.domain.activity.model.props.ActivityThumbnailProps;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.storage.dto.request.PresignUploadBatchRequest;
import CamNecT.server.global.storage.dto.request.PresignUploadRequest;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.service.PresignEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityAttachmentServiceAccountStateTest {

    @Mock AccountAccessGuard accountAccessGuard;
    @Mock PresignEngine presignEngine;
    @Mock UploadTicketRepository uploadTicketRepository;
    @Mock GlobalPresignMethods globalPresignMethods;

    private ActivityAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new ActivityAttachmentService(
                accountAccessGuard,
                presignEngine,
                uploadTicketRepository,
                globalPresignMethods,
                new ActivityAttachmentProps(10, 10, List.of("image/png", "application/pdf")),
                new ActivityThumbnailProps(10)
        );
    }

    @ParameterizedTest(name = "{0} actor cannot request {1} upload")
    @MethodSource("blockedPresignRequests")
    void inaccessibleActorCannotIssueUploadTicketOrPresignedUrl(UserStatus status, UploadKind uploadKind) {
        AuthErrorCode expected = status == UserStatus.WITHDRAWN
                ? AuthErrorCode.USER_WITHDRAWN
                : AuthErrorCode.USER_SUSPENDED;
        doThrow(new CustomException(expected))
                .when(accountAccessGuard).requireAccessibleForUpdate(1L);

        CustomException exception = assertThrows(CustomException.class, () -> invoke(uploadKind));

        assertThat(exception.getErrorCode()).isEqualTo(expected);
        verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        verifyNoInteractions(presignEngine, uploadTicketRepository, globalPresignMethods);
    }

    private void invoke(UploadKind uploadKind) {
        switch (uploadKind) {
            case THUMBNAIL -> service.presignThumbnail(
                    1L,
                    new PresignUploadRequest("image/png", 100L, "thumbnail.png")
            );
            case ATTACHMENT -> service.presignAttachmentsBatch(
                    1L,
                    new PresignUploadBatchRequest(List.of(
                            new PresignUploadBatchRequest.Item("application/pdf", 100L, "activity.pdf")
                    ))
            );
        }
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> blockedPresignRequests() {
        return Stream.of(UserStatus.SUSPENDED, UserStatus.WITHDRAWN)
                .flatMap(status -> Stream.of(UploadKind.values())
                        .map(kind -> org.junit.jupiter.params.provider.Arguments.of(status, kind)));
    }

    private enum UploadKind {
        THUMBNAIL,
        ATTACHMENT
    }
}
