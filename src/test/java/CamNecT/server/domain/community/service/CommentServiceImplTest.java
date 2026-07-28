package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.dto.request.CreateCommentRequest;
import CamNecT.server.domain.community.dto.request.UpdateCommentRequest;
import CamNecT.server.domain.community.dto.response.CommentListResponse;
import CamNecT.server.domain.community.model.Boards;
import CamNecT.server.domain.community.model.Comments.Comments;
import CamNecT.server.domain.community.model.Posts.PostStats;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.CommentStatus;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.community.repository.Comments.AcceptedCommentsRepository;
import CamNecT.server.domain.community.repository.Comments.CommentLikesRepository;
import CamNecT.server.domain.community.repository.Comments.CommentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostStatsRepository;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock PostsRepository postsRepository;
    @Mock CommentsRepository commentsRepository;
    @Mock PostStatsRepository postStatsRepository;
    @Mock CommentLikesRepository commentLikesRepository;
    @Mock UserRepository userRepository;
    @Mock AcceptedCommentsRepository acceptedCommentsRepository;
    @Mock AuthorAssembler authorAssembler;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks CommentServiceImpl service;

    @Test
    void acceptedCommentCannotBeUpdatedOrDeleted() {
        Comments comment = publishedComment(2L, null);
        when(commentsRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(comment));
        when(acceptedCommentsRepository.existsByComment_Id(20L)).thenReturn(true);

        CustomException updateError = assertThrows(CustomException.class,
                () -> service.update(2L, 20L, new UpdateCommentRequest("변경")));
        CustomException deleteError = assertThrows(CustomException.class,
                () -> service.delete(2L, 20L));

        assertThat(updateError.getErrorCode()).isEqualTo(CommunityErrorCode.CANNOT_MODIFY_ACCEPTED_COMMENT);
        assertThat(deleteError.getErrorCode()).isEqualTo(CommunityErrorCode.CANNOT_MODIFY_ACCEPTED_COMMENT);
        assertThat(comment.getContent()).isEqualTo("댓글");
        verifyNoInteractions(postStatsRepository);
    }

    @Test
    void replyToDeletedRootCommentIsAllowed() {
        Posts post = publishedPost();
        Comments deletedParent = publishedComment(2L, null);
        deletedParent.deleteSoft();
        PostStats stats = PostStats.init(post);

        when(postsRepository.findByIdForRead(10L)).thenReturn(Optional.of(post));
        when(commentsRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(deletedParent));
        when(commentsRepository.save(any(Comments.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postStatsRepository.findByPostIdForUpdate(10L)).thenReturn(Optional.of(stats));

        assertDoesNotThrow(() -> service.create(3L, 10L, new CreateCommentRequest("새 답글", 20L)));

        assertThat(stats.getCommentCount()).isEqualTo(1);
        assertThat(stats.getRootCommentCount()).isZero();
        verify(commentsRepository).save(argThat(saved -> saved.getParent() == deletedParent));
    }

    @Test
    void hiddenPostAndHiddenCommentRejectWrites() {
        Posts hiddenPost = Posts.builder()
                .id(10L)
                .board(Boards.of(BoardCode.INFO, "정보"))
                .user(Users.builder().userId(1L).build())
                .title("숨김")
                .content("본문")
                .status(CamNecT.server.domain.community.model.enums.PostStatus.HIDDEN)
                .build();
        when(postsRepository.findByIdForRead(10L)).thenReturn(Optional.of(hiddenPost));

        CustomException postError = assertThrows(CustomException.class,
                () -> service.create(2L, 10L, new CreateCommentRequest("댓글", null)));

        Comments hiddenComment = publishedComment(2L, null);
        hiddenComment.hide();
        when(commentsRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(hiddenComment));

        CustomException commentError = assertThrows(CustomException.class,
                () -> service.update(2L, 20L, new UpdateCommentRequest("변경")));

        assertThat(postError.getErrorCode()).isEqualTo(CommunityErrorCode.POST_NOT_PUBLISHED);
        assertThat(commentError.getErrorCode()).isEqualTo(CommunityErrorCode.COMMENT_NOT_PUBLISHED);
    }

    @Test
    void hiddenPostRejectsCommentListRead() {
        Posts hiddenPost = post(PostStatus.HIDDEN);
        when(postsRepository.findByIdForRead(10L)).thenReturn(Optional.of(hiddenPost));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.list(10L, null, 20));

        assertThat(exception.getErrorCode()).isEqualTo(CommunityErrorCode.POST_NOT_PUBLISHED);
        verifyNoInteractions(commentsRepository);
    }

    @Test
    void commentListReturnsNextRootCursorAndIncludesOnlyRequestedRootPage() {
        Posts post = publishedPost();
        Comments first = comment(post, 30L, 2L, null, CommentStatus.PUBLISHED);
        Comments second = comment(post, 20L, 3L, null, CommentStatus.DELETED);
        Comments extra = comment(post, 10L, 4L, null, CommentStatus.PUBLISHED);

        when(postsRepository.findByIdForRead(10L)).thenReturn(Optional.of(post));
        when(commentsRepository.findRootPage(
                eq(10L), eq(List.of(CommentStatus.PUBLISHED, CommentStatus.DELETED)), eq(40L), any(Pageable.class)
        )).thenReturn(List.of(first, second, extra));
        when(commentsRepository.findByPost_IdAndParent_IdInAndStatusInOrderByParent_IdAscCreatedAtAsc(
                eq(10L), eq(List.of(30L, 20L)), eq(List.of(CommentStatus.PUBLISHED, CommentStatus.DELETED))
        )).thenReturn(List.of());
        when(authorAssembler.buildAuthorMap(anyList())).thenReturn(Map.of());
        when(commentLikesRepository.countByCommentIds(anyList())).thenReturn(List.of());

        CommentListResponse response = service.list(10L, 40L, 2);

        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursorId()).isEqualTo(20L);
        assertThat(response.items()).extracting(item -> item.commentId())
                .containsExactly(30L, 20L);
        assertThat(response.items().get(1).userId()).isEqualTo(3L);
        assertThat(response.items().get(1).author()).isNull();
        verify(commentsRepository).findRootPage(
                eq(10L), anyCollection(), eq(40L), argThat(pageable -> pageable.getPageSize() == 3)
        );
    }

    private static Posts publishedPost() {
        return post(PostStatus.PUBLISHED);
    }

    private static Posts post(PostStatus status) {
        return Posts.builder()
                .id(10L)
                .board(Boards.of(BoardCode.QUESTION, "질문"))
                .user(Users.builder().userId(1L).build())
                .title("질문")
                .content("본문")
                .status(status)
                .build();
    }

    private static Comments publishedComment(Long userId, Comments parent) {
        return comment(publishedPost(), 20L, userId, parent, CommentStatus.PUBLISHED);
    }

    private static Comments comment(Posts post, Long id, Long userId, Comments parent, CommentStatus status) {
        return Comments.builder()
                .id(id)
                .post(post)
                .userId(userId)
                .parent(parent)
                .content("댓글")
                .status(status)
                .build();
    }
}
