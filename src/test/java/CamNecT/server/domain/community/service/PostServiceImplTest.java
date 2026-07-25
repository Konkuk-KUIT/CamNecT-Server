package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.dto.request.CreatePostRequest;
import CamNecT.server.domain.community.dto.request.UpdatePostRequest;
import CamNecT.server.domain.community.dto.response.PostDetailResponse;
import CamNecT.server.domain.community.model.Boards;
import CamNecT.server.domain.community.model.Comments.Comments;
import CamNecT.server.domain.community.model.Posts.PostStats;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.CommentStatus;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.community.repository.BoardsRepository;
import CamNecT.server.domain.community.repository.Comments.AcceptedCommentsRepository;
import CamNecT.server.domain.community.repository.Comments.CommentLikesRepository;
import CamNecT.server.domain.community.repository.Comments.CommentsRepository;
import CamNecT.server.domain.community.repository.Posts.*;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserFollowRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import CamNecT.server.global.point.service.PointService;
import CamNecT.server.global.notification.event.SimpleNotifiableEvent;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.PresignEngine;
import CamNecT.server.global.tag.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock BoardsRepository boardsRepository;
    @Mock PostsRepository postsRepository;
    @Mock PostStatsRepository postStatsRepository;
    @Mock PostTagsRepository postTagsRepository;
    @Mock PostLikesRepository postLikesRepository;
    @Mock AcceptedCommentsRepository acceptedCommentsRepository;
    @Mock CommentsRepository commentsRepository;
    @Mock CommentLikesRepository commentLikesRepository;
    @Mock PostBookmarksRepository postBookmarksRepository;
    @Mock PostAccessRepository postAccessRepository;
    @Mock PostAttachmentsRepository postAttachmentsRepository;
    @Mock TagRepository tagRepository;
    @Mock UserRepository userRepository;
    @Mock UserFollowRepository followRepository;
    @Mock UploadTicketRepository uploadTicketRepository;
    @Mock PostAttachmentsService postAttachmentsService;
    @Mock PointService pointService;
    @Mock PresignEngine presignEngine;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AuthorAssembler authorAssembler;

    @InjectMocks PostServiceImpl service;

    @Test
    void emptyPostUpdateIsRejectedBeforeDatabaseAccess() {
        CustomException exception = assertThrows(CustomException.class,
                () -> service.update(1L, 10L, new UpdatePostRequest(null, null, null, null, null)));

        assertThat(exception.getErrorCode()).isEqualTo(CommunityErrorCode.EMPTY_POST_UPDATE);
        verifyNoInteractions(postsRepository);
    }

    @Test
    void anonymousPostDoesNotPublishFollowerNotification() {
        Users author = Users.builder().userId(1L).name("작성자").build();
        Boards board = Boards.of(BoardCode.INFO, "정보");
        CreatePostRequest request = new CreatePostRequest(
                BoardCode.INFO, "제목", "본문", true, null, null
        );

        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(author));
        when(boardsRepository.findByCode(BoardCode.INFO)).thenReturn(Optional.of(board));
        when(postsRepository.save(any(Posts.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(1L, request);

        verifyNoInteractions(followRepository, eventPublisher);
    }

    @Test
    void questionAuthorCannotAcceptOwnComment() {
        Posts post = post(1L, BoardCode.QUESTION, false, PostStatus.PUBLISHED);
        Comments ownComment = Comments.builder()
                .id(20L)
                .post(post)
                .userId(1L)
                .content("자기 답변")
                .status(CommentStatus.PUBLISHED)
                .build();

        when(postsRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
        when(commentsRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(ownComment));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.acceptComment(1L, 10L, 20L));

        assertThat(exception.getErrorCode()).isEqualTo(CommunityErrorCode.CANNOT_ACCEPT_OWN_COMMENT);
        verify(acceptedCommentsRepository, never()).saveAndFlush(any());
        verifyNoInteractions(pointService);
    }

    @Test
    void anonymousQuestionAcceptanceNotificationOmitsActor() {
        Posts post = post(1L, BoardCode.QUESTION, true, PostStatus.PUBLISHED);
        Comments comment = Comments.builder()
                .id(20L)
                .post(post)
                .userId(2L)
                .content("답변")
                .status(CommentStatus.PUBLISHED)
                .build();
        when(postsRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
        when(commentsRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(comment));

        service.acceptComment(1L, 10L, 20L);

        ArgumentCaptor<SimpleNotifiableEvent> eventCaptor = ArgumentCaptor.forClass(SimpleNotifiableEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().actorUserId()).isNull();
    }

    @Test
    void anonymousDetailOmitsAuthorAndUsesAtomicViewIncrement() {
        Posts post = post(1L, BoardCode.INFO, true, PostStatus.PUBLISHED);
        PostStats stats = PostStats.init(post);

        when(postsRepository.findByIdForRead(10L)).thenReturn(Optional.of(post));
        when(postStatsRepository.incrementView(eq(10L), any())).thenReturn(1);
        when(postStatsRepository.findByPost_Id(10L)).thenReturn(Optional.of(stats));
        when(postTagsRepository.findByPost_Id(10L)).thenReturn(List.of());
        when(acceptedCommentsRepository.findByPost_Id(10L)).thenReturn(Optional.empty());
        when(postAttachmentsRepository.findByPost_IdAndStatusTrueOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of());

        PostDetailResponse result = service.getDetail(2L, 10L);

        assertThat(result.content()).isEqualTo("본문");
        assertThat(result.anonymous()).isTrue();
        assertThat(result.author()).isNull();
        verifyNoInteractions(authorAssembler);
        verify(postStatsRepository).incrementView(eq(10L), any());
    }

    @Test
    void bookmarkUsesLockedStatsAndRejectsHiddenPost() {
        Users user = Users.builder().userId(2L).build();
        Posts published = post(1L, BoardCode.INFO, false, PostStatus.PUBLISHED);
        PostStats stats = PostStats.init(published);

        when(userRepository.findByUserId(2L)).thenReturn(Optional.of(user));
        when(postsRepository.findByIdForRead(10L)).thenReturn(Optional.of(published));
        when(postStatsRepository.findByPostIdForUpdate(10L)).thenReturn(Optional.of(stats));

        service.toggleBookmark(2L, 10L);

        verify(postStatsRepository).findByPostIdForUpdate(10L);
        assertThat(stats.getBookmarkCount()).isEqualTo(1);

        Posts hidden = post(1L, BoardCode.INFO, false, PostStatus.HIDDEN);
        when(postsRepository.findByIdForRead(11L)).thenReturn(Optional.of(hidden));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.toggleBookmark(2L, 11L));

        assertThat(exception.getErrorCode()).isEqualTo(CommunityErrorCode.POST_NOT_PUBLISHED);
        verify(postStatsRepository, never()).findByPostIdForUpdate(11L);
    }

    private static Posts post(Long authorId, BoardCode boardCode, boolean anonymous, PostStatus status) {
        return Posts.builder()
                .id(10L)
                .board(Boards.of(boardCode, boardCode.name()))
                .user(Users.builder().userId(authorId).build())
                .title("제목")
                .content("본문")
                .isAnonymous(anonymous)
                .status(status)
                .build();
    }
}
