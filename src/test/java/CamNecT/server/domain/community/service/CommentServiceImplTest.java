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
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
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
    @Mock CommunityPostAccessPolicy postAccessPolicy;
    @Mock AccountAccessGuard accountAccessGuard;
    @Mock AuthorAssembler authorAssembler;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks CommentServiceImpl service;

    @Test
    void acceptedCommentCannotBeUpdatedOrDeleted() {
        Comments comment = publishedComment(2L, null);
        when(accountAccessGuard.requireAccessibleForUpdate(2L))
                .thenReturn(Users.builder().userId(2L).role(UserRole.USER).build());
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
                () -> service.list(2L, 10L, null, 20));

        assertThat(exception.getErrorCode()).isEqualTo(CommunityErrorCode.POST_NOT_PUBLISHED);
        verifyNoInteractions(commentsRepository);
    }

    @Test
    void unreadablePaidQuestionRejectsCommentListBeforeQueryingComments() {
        Posts post = publishedPost();
        when(postsRepository.findByIdForRead(10L)).thenReturn(Optional.of(post));
        doThrow(new CustomException(CommunityErrorCode.POST_FORBIDDEN))
                .when(postAccessPolicy).requireReadable(2L, post, false);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.list(2L, 10L, null, 20));

        assertThat(exception.getErrorCode()).isEqualTo(CommunityErrorCode.POST_FORBIDDEN);
        verifyNoInteractions(commentsRepository);
    }

    @Test
    void unreadablePaidQuestionRejectsNewCommentAndLike() {
        Posts post = publishedPost();
        Comments comment = comment(post, 20L, 2L, null, CommentStatus.PUBLISHED);
        when(postsRepository.findByIdForRead(10L)).thenReturn(Optional.of(post));
        when(commentsRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(comment));
        doThrow(new CustomException(CommunityErrorCode.POST_FORBIDDEN))
                .when(postAccessPolicy).requireReadable(2L, post, false);

        assertThrows(CustomException.class,
                () -> service.create(2L, 10L, new CreateCommentRequest("댓글", null)));
        assertThrows(CustomException.class,
                () -> service.toggleLike(2L, 20L));

        verify(commentsRepository, never()).save(any());
        verifyNoInteractions(postStatsRepository, commentLikesRepository);
    }

    @Test
    void paywallDoesNotPreventAuthorOrAdminFromDeletingExistingComment() {
        Posts post = publishedPost();
        Comments ownerComment = comment(post, 20L, 2L, null, CommentStatus.PUBLISHED);
        Comments moderatedComment = comment(post, 21L, 3L, null, CommentStatus.PUBLISHED);
        PostStats stats = PostStats.init(post);
        when(accountAccessGuard.requireAccessibleForUpdate(2L))
                .thenReturn(Users.builder().userId(2L).role(UserRole.USER).build());
        when(accountAccessGuard.requireAccessibleForUpdate(99L))
                .thenReturn(Users.builder().userId(99L).role(UserRole.ADMIN).build());
        when(commentsRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(ownerComment));
        when(commentsRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(moderatedComment));
        when(postStatsRepository.findByPostIdForUpdate(10L)).thenReturn(Optional.of(stats));
        assertDoesNotThrow(() -> service.delete(2L, 20L));
        assertDoesNotThrow(() -> service.delete(99L, 21L));

        assertThat(ownerComment.getStatus()).isEqualTo(CommentStatus.DELETED);
        assertThat(moderatedComment.getStatus()).isEqualTo(CommentStatus.DELETED);
        verify(userRepository, never()).existsByUserIdAndRole(anyLong(), eq(UserRole.ADMIN));
        verify(postAccessPolicy, never()).requireReadable(anyLong(), any(), anyBoolean());
    }

    @Test
    void inaccessibleActorIsRejectedBeforeCommentLock() {
        doThrow(new CustomException(AuthErrorCode.USER_SUSPENDED))
                .when(accountAccessGuard).requireAccessibleForUpdate(2L);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.toggleLike(2L, 20L));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_SUSPENDED);
        verify(accountAccessGuard).requireAccessibleForUpdate(2L);
        verifyNoInteractions(commentsRepository, commentLikesRepository, postStatsRepository);
    }

    @Test
    void administratorBypassIsLimitedToCommentRead() {
        Posts post = publishedPost();
        when(postsRepository.findByIdForRead(10L)).thenReturn(Optional.of(post));
        when(userRepository.existsByUserIdAndRole(99L, UserRole.ADMIN)).thenReturn(true);
        when(commentsRepository.findRootPage(
                eq(10L), eq(List.of(CommentStatus.PUBLISHED, CommentStatus.DELETED)),
                isNull(), any(Pageable.class)
        )).thenReturn(List.of());

        assertDoesNotThrow(() -> service.list(99L, 10L, null, 20));
        verify(postAccessPolicy).requireReadable(99L, post, true);

        doThrow(new CustomException(CommunityErrorCode.POST_FORBIDDEN))
                .when(postAccessPolicy).requireReadable(99L, post, false);

        assertThrows(CustomException.class,
                () -> service.create(99L, 10L, new CreateCommentRequest("관리자 댓글", null)));
        verify(commentsRepository, never()).save(any());
    }

    @Test
    void moderationDeletionRemovesAcceptedAnswer() {
        Posts post = publishedPost();
        Comments acceptedAnswer = comment(post, 20L, 2L, null, CommentStatus.PUBLISHED);
        PostStats stats = PostStats.init(post);
        when(commentsRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(acceptedAnswer));
        when(userRepository.existsByUserIdAndRole(99L, UserRole.ADMIN)).thenReturn(true);
        when(postStatsRepository.findByPostIdForUpdate(10L)).thenReturn(Optional.of(stats));

        assertDoesNotThrow(() -> service.deleteForModeration(99L, 20L));

        verify(acceptedCommentsRepository).deleteByComment_Id(20L);
        assertThat(acceptedAnswer.getStatus()).isEqualTo(CommentStatus.DELETED);
        verifyNoInteractions(accountAccessGuard);
    }

    @Test
    void moderationDeletionTreatsHiddenCommentAsComplete() {
        Comments hidden = publishedComment(2L, null);
        hidden.hide();
        when(commentsRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(hidden));
        when(userRepository.existsByUserIdAndRole(99L, UserRole.ADMIN)).thenReturn(true);

        assertDoesNotThrow(() -> service.deleteForModeration(99L, 20L));

        verify(acceptedCommentsRepository, never()).deleteByComment_Id(anyLong());
        verifyNoInteractions(postStatsRepository);
    }

    @Test
    void commentListReturnsNextRootCursorAndIncludesOnlyRequestedRootPage() {
        Posts post = publishedPost();
        LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 8, 24, 10, 0);
        LocalDateTime secondCreatedAt = LocalDateTime.of(2026, 8, 24, 9, 0);
        Comments first = comment(post, 30L, 2L, null, CommentStatus.PUBLISHED, firstCreatedAt);
        Comments second = comment(post, 20L, 3L, null, CommentStatus.DELETED, secondCreatedAt);
        Comments extra = comment(post, 10L, 4L, null, CommentStatus.PUBLISHED,
                LocalDateTime.of(2026, 8, 24, 8, 0));

        when(postsRepository.findByIdForRead(10L)).thenReturn(Optional.of(post));
        when(commentsRepository.findRootPage(
                eq(10L), eq(List.of(CommentStatus.PUBLISHED, CommentStatus.DELETED)), eq(40L), any(Pageable.class)
        )).thenReturn(List.of(first, second, extra));
        when(commentsRepository.findByPost_IdAndParent_IdInAndStatusInOrderByParent_IdAscCreatedAtAsc(
                eq(10L), eq(List.of(30L, 20L)), eq(List.of(CommentStatus.PUBLISHED, CommentStatus.DELETED))
        )).thenReturn(List.of());
        when(authorAssembler.buildAuthorMap(anyList())).thenReturn(Map.of());
        when(commentLikesRepository.countByCommentIds(anyList())).thenReturn(List.of());

        CommentListResponse response = service.list(2L, 10L, 40L, 2);

        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursorId()).isEqualTo(20L);
        assertThat(response.items()).extracting(item -> item.commentId())
                .containsExactly(30L, 20L);
        assertThat(response.items()).extracting(item -> item.createdAt())
                .containsExactly(firstCreatedAt, secondCreatedAt);
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
        return comment(post, id, userId, parent, status, null);
    }

    private static Comments comment(Posts post, Long id, Long userId, Comments parent, CommentStatus status,
                                    LocalDateTime createdAt) {
        return Comments.builder()
                .id(id)
                .post(post)
                .userId(userId)
                .parent(parent)
                .content("댓글")
                .status(status)
                .createdAt(createdAt)
                .build();
    }
}
