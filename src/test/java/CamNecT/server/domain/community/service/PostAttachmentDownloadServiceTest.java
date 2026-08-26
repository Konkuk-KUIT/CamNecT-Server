package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.model.Boards;
import CamNecT.server.domain.community.model.Posts.PostAttachments;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.PostAccessType;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.community.repository.Posts.PostAttachmentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.dto.response.PresignDownloadResponse;
import CamNecT.server.global.storage.service.PresignEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostAttachmentDownloadServiceTest {

    @Mock PostsRepository postsRepository;
    @Mock PostAttachmentsRepository postAttachmentsRepository;
    @Mock CommunityPostAccessPolicy postAccessPolicy;
    @Mock UserRepository userRepository;
    @Mock PresignEngine presignEngine;
    @Mock UploadTicketRepository uploadTicketRepository;

    @InjectMocks PostAttachmentDownloadService service;

    @Test
    void unreadablePaidQuestionIsRejectedBeforeAttachmentLookup() {
        Posts post = Posts.builder()
                .id(10L)
                .board(Boards.of(BoardCode.QUESTION, "질문"))
                .user(Users.builder().userId(1L).build())
                .title("질문")
                .content("본문")
                .status(PostStatus.PUBLISHED)
                .accessType(PostAccessType.POINT_REQUIRED)
                .build();
        when(postsRepository.findById(10L)).thenReturn(Optional.of(post));
        doThrow(new CustomException(CommunityErrorCode.POST_FORBIDDEN))
                .when(postAccessPolicy).requireReadable(2L, post, false);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.presignDownload(2L, 10L, 100L));

        assertThat(exception.getErrorCode()).isEqualTo(CommunityErrorCode.POST_FORBIDDEN);
        verifyNoInteractions(postAttachmentsRepository, uploadTicketRepository, presignEngine);
    }

    @Test
    void administratorCanIssuePaidPostAttachmentDownloadWithoutPurchase() {
        Posts post = Posts.builder()
                .id(10L)
                .board(Boards.of(BoardCode.QUESTION, "질문"))
                .user(Users.builder().userId(1L).build())
                .title("질문")
                .content("보호 본문")
                .status(PostStatus.PUBLISHED)
                .accessType(PostAccessType.POINT_REQUIRED)
                .build();
        PostAttachments attachment = PostAttachments.builder()
                .id(100L)
                .post(post)
                .fileKey("community/evidence.pdf")
                .status(true)
                .build();
        PresignDownloadResponse expected = new PresignDownloadResponse(
                "https://download", LocalDateTime.of(2026, 8, 26, 12, 0), attachment.getFileKey()
        );

        when(postsRepository.findById(10L)).thenReturn(Optional.of(post));
        when(userRepository.existsByUserIdAndRole(99L, UserRole.ADMIN)).thenReturn(true);
        when(postAttachmentsRepository.findByIdAndPost_IdAndStatusTrue(100L, 10L))
                .thenReturn(Optional.of(attachment));
        when(uploadTicketRepository.findByStorageKey(attachment.getFileKey())).thenReturn(Optional.empty());
        when(presignEngine.presignDownload(attachment.getFileKey(), null, null)).thenReturn(expected);

        PresignDownloadResponse result = service.presignDownload(99L, 10L, 100L);

        assertThat(result).isEqualTo(expected);
        verify(postAccessPolicy).requireReadable(99L, post, true);
    }
}
