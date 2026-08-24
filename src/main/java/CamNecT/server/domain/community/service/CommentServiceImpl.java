package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.dto.AuthorDto;
import CamNecT.server.domain.community.dto.request.CreateCommentRequest;
import CamNecT.server.domain.community.dto.request.UpdateCommentRequest;
import CamNecT.server.domain.community.dto.response.CommentItemResponse;
import CamNecT.server.domain.community.dto.response.CommentListResponse;
import CamNecT.server.domain.community.dto.response.CreateCommentResponse;
import CamNecT.server.domain.community.dto.response.ToggleCommentLikeResponse;
import CamNecT.server.domain.community.model.Comments.CommentLikes;
import CamNecT.server.domain.community.model.Comments.Comments;
import CamNecT.server.domain.community.model.Posts.PostStats;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.CommentStatus;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.community.repository.Comments.AcceptedCommentsRepository;
import CamNecT.server.domain.community.repository.Comments.CommentLikesRepository;
import CamNecT.server.domain.community.repository.Comments.CommentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostStatsRepository;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import CamNecT.server.global.notification.event.SimpleNotifiableEvent;
import CamNecT.server.global.notification.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final PostsRepository postsRepository;
    private final CommentsRepository commentsRepository;
    private final PostStatsRepository postStatsRepository;
    private final CommentLikesRepository commentLikesRepository;
    private final UserRepository userRepository;
    private final AcceptedCommentsRepository acceptedCommentsRepository;
    private final AuthorAssembler  authorAssembler;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public CreateCommentResponse create(Long userId, Long postId, CreateCommentRequest req) {
        if (userId == null) throw new CustomException(AuthErrorCode.INVALID_TOKEN);

        Posts post = postsRepository.findByIdForRead(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));

        requirePublished(post);

        Comments parent = null;
        if (req.parentCommentId() != null) {
            parent = commentsRepository.findByIdForUpdate(req.parentCommentId())
                    .orElseThrow(() -> new CustomException(CommunityErrorCode.PARENT_COMMENT_NOT_FOUND));

            if (!Objects.equals(parent.getPost().getId(), postId)) {
                throw new CustomException(CommunityErrorCode.PARENT_COMMENT_NOT_IN_POST);
            }

            // 삭제 댓글은 대화 맥락 유지를 위해 답글을 허용하고, 숨김 댓글만 차단한다.
            if (parent.getStatus() == CommentStatus.HIDDEN) {
                throw new CustomException(CommunityErrorCode.CANNOT_REPLY_TO_HIDDEN_COMMENT);
            }

            // 2뎁스 제한: 부모가 이미 대댓글이면 금지
            if (parent.getParent() != null) {
                throw new CustomException(CommunityErrorCode.COMMENT_MAX_DEPTH_EXCEEDED);
            }
        }

        Comments saved = commentsRepository.save(Comments.create(post, userId, parent, req.content()));

        PostStats stats = postStatsRepository.findByPostIdForUpdate(postId)
                .orElseGet(() -> postStatsRepository.save(PostStats.init(post)));

        /// 스탯 관련 조치
        stats.incComment();               // 전체 댓글 수 +1
        if (parent == null) {
            stats.incRootComment();       // 루트 댓글 수(=답변 수) +1
        }
        stats.touch();
        //repo 팔로워들 불러오기

        /// 알림 이벤트 발행
        if (parent == null) {
            Long receiverId = post.getUser().getUserId();
            eventPublisher.publishEvent(SimpleNotifiableEvent.of(
                    receiverId,
                    userId,
                    NotificationType.POST_COMMENTED,
                    "게시글에 댓글이 달렸습니다.",
                    postId,
                    saved.getId()
            ));
        } else {
            Long receiverId = parent.getUserId();
            eventPublisher.publishEvent(SimpleNotifiableEvent.of(
                    receiverId,
                    userId,
                    NotificationType.COMMENT_REPLIED,
                    "내 댓글에 답글이 달렸습니다.",
                    postId,
                    saved.getId()
            ));
        }

        return new CreateCommentResponse(saved.getId());
    }

    @Transactional
    @Override
    public void update(Long userId, Long commentId, UpdateCommentRequest req) {
        if (userId == null) throw new CustomException(AuthErrorCode.INVALID_TOKEN);

        Comments comment = commentsRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.COMMENT_NOT_FOUND));

        requirePublished(comment.getPost());
        requirePublished(comment);

        if (!Objects.equals(comment.getUserId(), userId)) {
            throw new CustomException(CommunityErrorCode.COMMENT_FORBIDDEN);
        }
        if (acceptedCommentsRepository.existsByComment_Id(commentId)) {
            throw new CustomException(CommunityErrorCode.CANNOT_MODIFY_ACCEPTED_COMMENT);
        }

        comment.update(req.content());
        touchStats(comment.getPost().getId());
    }

    @Transactional
    @Override
    public void delete(Long userId, Long commentId) {
        if (userId == null) throw new CustomException(AuthErrorCode.INVALID_TOKEN);

        Comments comment = commentsRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.COMMENT_NOT_FOUND));

        requirePublished(comment.getPost());
        requirePublished(comment);

        boolean isAdmin = userRepository.existsByUserIdAndRole(userId, UserRole.ADMIN);
        boolean isOwner = Objects.equals(comment.getUserId(), userId);

        //작성자 또는 관리자만 삭제 가능
        if (!(isAdmin || isOwner)) {
            throw new CustomException(CommunityErrorCode.COMMENT_FORBIDDEN);
        }
        if (acceptedCommentsRepository.existsByComment_Id(commentId)) {
            throw new CustomException(CommunityErrorCode.CANNOT_MODIFY_ACCEPTED_COMMENT);
        }

        boolean isRoot = (comment.getParent() == null);

        comment.deleteSoft();

        PostStats stats = postStatsRepository.findByPostIdForUpdate(comment.getPost().getId())
                .orElseGet(() -> postStatsRepository.save(PostStats.init(comment.getPost())));

        stats.decComment();
        if (isRoot) stats.decRootComment();
        stats.touch();
    }

    @Transactional
    @Override
    public ToggleCommentLikeResponse toggleLike(Long userId, Long commentId) {
        if (userId == null) throw new CustomException(AuthErrorCode.INVALID_TOKEN);

        Comments comment = commentsRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.COMMENT_NOT_FOUND));

        requirePublished(comment.getPost());
        requirePublished(comment);

        boolean liked;
        if (commentLikesRepository.existsByComment_IdAndUserId(commentId, userId)) {
            commentLikesRepository.deleteByComment_IdAndUserId(commentId, userId);
            liked = false;
        } else {
            commentLikesRepository.save(CommentLikes.of(comment, userId));
            liked = true;
        }

        long likeCount = commentLikesRepository.countByComment_Id(commentId);

        // (선택) 댓글에 추천이 찍히면 게시글도 “활동”으로 취급하고 싶을 때
        touchStats(comment.getPost().getId());

        return new ToggleCommentLikeResponse(liked, likeCount);
    }

    @Transactional(readOnly = true)
    @Override
    public CommentListResponse list(Long postId, Long cursorId, int size) {
        Posts post = postsRepository.findByIdForRead(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));
        requirePublished(post);

        if (cursorId != null && cursorId <= 0) {
            throw new CustomException(CommunityErrorCode.INVALID_CURSOR);
        }

        int limit = Math.clamp(size, 1, 50);
        var pageable = PageRequest.of(0, limit + 1);
        List<CommentStatus> visibleStatuses = List.of(CommentStatus.PUBLISHED, CommentStatus.DELETED);
        List<Comments> fetchedRoots = commentsRepository.findRootPage(postId, visibleStatuses, cursorId, pageable);
        boolean hasNext = fetchedRoots.size() > limit;
        List<Comments> roots = hasNext
                ? new ArrayList<>(fetchedRoots.subList(0, limit))
                : new ArrayList<>(fetchedRoots);
        Long nextCursorId = hasNext && !roots.isEmpty() ? roots.getLast().getId() : null;

        List<Long> rootIds = roots.stream().map(Comments::getId).toList();

        // 자식 댓글: 부모 아래 created_at asc
        List<Comments> children = rootIds.isEmpty() ? List.of() : mergeChildren(postId, rootIds);

        // author bulk
        List<Long> userIds = Stream.concat(roots.stream(), children.stream())
                .map(Comments::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, AuthorDto> authorMap = authorAssembler.buildAuthorMap(userIds);

        // 좋아요 수 배치 집계 (옵션 쿼리 사용)
        Map<Long, Long> likeMap = buildLikeCountMap(roots, children);

        // flat list 구성: 루트 -> 자식 순서로 내려줌
        List<CommentItemResponse> out = new ArrayList<>();

        // parentId -> children list
        Map<Long, List<Comments>> childMap = new LinkedHashMap<>();
        for (Comments ch : children) {
            childMap.computeIfAbsent(ch.getParent().getId(), k -> new ArrayList<>()).add(ch);
        }

        for (Comments r : roots) {
            out.add(toRow(r, likeMap.getOrDefault(r.getId(), 0L), authorMap));

            List<Comments> kids = childMap.getOrDefault(r.getId(), List.of());
            for (Comments k : kids) {
                out.add(toRow(k, likeMap.getOrDefault(k.getId(), 0L), authorMap));
            }
        }

        return new CommentListResponse(out, nextCursorId, hasNext);
    }

    private List<Comments> mergeChildren(Long postId, List<Long> rootIds) {
        List<Comments> children = new ArrayList<>(
                commentsRepository.findByPost_IdAndParent_IdInAndStatusInOrderByParent_IdAscCreatedAtAsc(
                        postId, rootIds, List.of(CommentStatus.PUBLISHED, CommentStatus.DELETED)
                )
        );
        // parent_id asc, created_at asc 유지되도록 한 번 더 안정 정렬
        children.sort(Comparator
                .comparing((Comments c) -> c.getParent().getId())
                .thenComparing(Comments::getCreatedAt)
                .thenComparing(Comments::getId));
        return children;
    }

    private Map<Long, Long> buildLikeCountMap(List<Comments> roots, List<Comments> children) {
        List<Long> ids = new ArrayList<>(roots.size() + children.size());
        for (Comments c : roots) ids.add(c.getId());
        for (Comments c : children) ids.add(c.getId());
        if (ids.isEmpty()) return Map.of();

        Map<Long, Long> map = new HashMap<>();
        for (CommentLikesRepository.LikeCountRow row : commentLikesRepository.countByCommentIds(ids)) {
            map.put(row.getCommentId(), row.getCnt());
        }
        return map;
    }

    private CommentItemResponse toRow(Comments c, long likeCount, Map<Long, AuthorDto> authorMap) {
        boolean deleted = c.getStatus().isDeleted();
        String content = deleted ? "삭제된 댓글입니다." : c.getContent();
        Long parentId = (c.getParent() == null) ? null : c.getParent().getId();

        AuthorDto author = deleted ? null : authorMap.get(c.getUserId());

        return new CommentItemResponse(
                c.getId(),
                c.getUserId(),
                parentId,
                content,
                likeCount,
                c.getCreatedAt(),
                author
        );
    }

    private void requirePublished(Posts post) {
        if (!post.getStatus().isPublished()) {
            throw new CustomException(CommunityErrorCode.POST_NOT_PUBLISHED);
        }
    }

    private void requirePublished(Comments comment) {
        if (!comment.getStatus().isPublished()) {
            throw new CustomException(CommunityErrorCode.COMMENT_NOT_PUBLISHED);
        }
    }

    private void touchStats(Long postId) {
        postStatsRepository.touchByPostId(postId, LocalDateTime.now());
    }
}
