package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.model.Boards;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.PostAccessType;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.community.repository.Posts.PostAttachmentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.PresignEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostAttachmentDownloadServiceTest {

    @Mock PostsRepository postsRepository;
    @Mock PostAttachmentsRepository postAttachmentsRepository;
    @Mock CommunityPostAccessPolicy postAccessPolicy;
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
                .when(postAccessPolicy).requireReadable(2L, post);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.presignDownload(2L, 10L, 100L));

        assertThat(exception.getErrorCode()).isEqualTo(CommunityErrorCode.POST_FORBIDDEN);
        verifyNoInteractions(postAttachmentsRepository, uploadTicketRepository, presignEngine);
    }
}
