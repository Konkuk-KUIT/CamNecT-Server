package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.dto.AuthorDto;
import CamNecT.server.domain.community.dto.request.CreatePostRequest;
import CamNecT.server.domain.community.dto.request.UpdatePostRequest;
import CamNecT.server.domain.community.dto.response.*;
import CamNecT.server.domain.community.model.Boards;
import CamNecT.server.domain.community.model.Comments.AcceptedComments;
import CamNecT.server.domain.community.model.Comments.Comments;
import CamNecT.server.domain.community.model.Posts.*;
import CamNecT.server.domain.community.model.enums.*;
import CamNecT.server.domain.community.repository.BoardsRepository;
import CamNecT.server.domain.community.repository.Comments.AcceptedCommentsRepository;
import CamNecT.server.domain.community.repository.Comments.CommentLikesRepository;
import CamNecT.server.domain.community.repository.Comments.CommentsRepository;
import CamNecT.server.domain.community.repository.Posts.*;
import CamNecT.server.domain.users.repository.UserFollowRepository;
import CamNecT.server.global.point.model.PointEvent;
import CamNecT.server.global.point.service.PointService;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import CamNecT.server.global.notification.event.SimpleNotifiableEvent;
import CamNecT.server.global.notification.model.NotificationType;
import CamNecT.server.global.storage.model.UploadTicket;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.PresignEngine;
import CamNecT.server.global.tag.model.Tag;
import CamNecT.server.global.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    @Value("${app.point.reward.comment-selection:200}")
    private int rewardAcceptedComment;
    @Value("${app.point.reward.first-three-likes:100}")
    private int rewardFirstThreeLikes;
    @Value("${app.point.cost.question-view:100}")
    private int questionViewCost;
    private static final long LEGACY_TAG_ID = 111L;
    private static final long CANONICAL_TAG_ID = 53L;


    private final BoardsRepository boardsRepository;
    private final PostsRepository postsRepository;
    private final PostStatsRepository postStatsRepository;
    private final PostTagsRepository postTagsRepository;


    private final PostLikesRepository postLikesRepository;
    private final AcceptedCommentsRepository acceptedCommentsRepository;
    private final CommentsRepository commentsRepository;
    private final CommentLikesRepository commentLikesRepository;
    private final PostBookmarksRepository postBookmarksRepository;
    private final PostAccessRepository postAccessRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;

    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final UserFollowRepository followRepository;
    private final UploadTicketRepository uploadTicketRepository;

    private final PostAttachmentsService postAttachmentsService;
    private final PointService pointService;
    private final PresignEngine presignEngine;

    private final ApplicationEventPublisher eventPublisher;
    private final AuthorAssembler authorAssembler;

    @Transactional
    @Override
    public CreatePostResponse create(Long userId, CreatePostRequest req) {
        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));

        Boards board = boardsRepository.findByCode(req.boardCode())
                .orElseThrow(() -> new CustomException(CommunityErrorCode.BOARD_NOT_FOUND));

        PostAccessType accessType = (req.boardCode() == BoardCode.QUESTION) ? PostAccessType.POINT_REQUIRED : PostAccessType.FREE;

        Posts post = Posts.create(board, user, req.title().trim(), req.content(), Boolean.TRUE.equals(req.anonymous()));
        post.applyAccess(accessType);

        Posts saved = postsRepository.save(post);
        postStatsRepository.save(PostStats.init(saved));
        log.info("[PostCreate] 게시글 저장 완료 - postId={}", saved.getId());

        replaceTags(saved, req.tagIds());

        postAttachmentsService.replace(saved, userId, req.attachments());

        // 익명 게시글은 팔로워 알림만으로도 작성자가 역추적될 수 있으므로 발행하지 않는다.
        if (!saved.isAnonymous()) {
            List<Long> followerIds = followRepository.findFollowerIdsByFollowingId(userId);

            if (!followerIds.isEmpty()) {
                String message = user.getName() + "님이 새 글을 게시했습니다.";

                for (Long followerId : followerIds) {
                    eventPublisher.publishEvent(SimpleNotifiableEvent.of(
                            followerId,
                            userId,
                            NotificationType.FOLLOWING_POSTED,
                            message,
                            saved.getId(),
                            null
                    ));
                }
            }
        }

        return new CreatePostResponse(saved.getId());
    }

    @Transactional
    @Override
    public void update(Long userId, Long postId, UpdatePostRequest req) {
        if (userId == null) throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        if (req == null || !req.isAnyFieldPresent()) {
            throw new CustomException(CommunityErrorCode.EMPTY_POST_UPDATE);
        }

        Posts post = postsRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));

        requirePublished(post);

        if (!Objects.equals(post.getUser().getUserId(), userId)) {
            throw new CustomException(CommunityErrorCode.POST_FORBIDDEN);
        }

        post.update(req.title() == null ? null : req.title().trim(), req.content(), req.anonymous());

        if (req.tagIds() != null) {
            postTagsRepository.deleteByPost_Id(postId);
            replaceTags(post, req.tagIds());
        }
        if (req.attachments() != null) {
            postAttachmentsService.replace(post, userId, req.attachments());
        }

        touchStats(postId);
    }

    @Transactional
    @Override
    public void delete(Long userId, Long postId) {
        if (userId == null) throw new CustomException(AuthErrorCode.INVALID_TOKEN);

        Posts post = postsRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));

        boolean isAdmin = userRepository.existsByUserIdAndRole(userId, UserRole.ADMIN);
        boolean isOwner = Objects.equals(post.getUser().getUserId(), userId);

        if (!(isAdmin || isOwner)) {
            throw new CustomException(CommunityErrorCode.POST_FORBIDDEN);
        } //Admin이거나 작성자면은 스킵

        if (post.getBoard().getCode() == BoardCode.QUESTION
                && acceptedCommentsRepository.existsByPost_Id(postId)) {
            throw new CustomException(CommunityErrorCode.CANNOT_DELETE_ACCEPTED_QUESTION);
        }
        // 1) 댓글 좋아요 -> 댓글 하드 삭제 (FK 안전)
        commentLikesRepository.deleteByPostId(postId);
        commentsRepository.deleteByPostId(postId);

        // 2) 게시글 좋아요/북마크/구매권한 정리
        postLikesRepository.deleteByPostId(postId);
        postBookmarksRepository.deleteByPostId(postId);
        postAccessRepository.deleteByPostId(postId);
        postStatsRepository.deleteByPostId(postId);


        // 3) 태그/채택 정리
        postTagsRepository.deleteByPost_Id(postId);
        acceptedCommentsRepository.deleteByPost_Id(postId);

        // 4) 첨부 정리 (S3 after-commit 삭제 포함)
        postAttachmentsService.purgeAllByPostId(postId);

        postsRepository.softDeleteById(postId, LocalDateTime.now());
    }

    @Transactional
    @Override
    public ToggleLikeResponse toggleLike(Long userId, Long postId) {
        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));

        Posts post = postsRepository.findByIdForRead(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));

        requirePublished(post);

        PostStats stats = postStatsRepository.findByPostIdForUpdate(postId)
                .orElseGet(() -> postStatsRepository.save(PostStats.init(post)));

        //작성자면 본인글 좋아요 불가
        if (Objects.equals(userId, post.getUser().getUserId()))
            throw new CustomException(CommunityErrorCode.CANNOT_LIKE_OWN_POST);

        boolean liked;
        if (postLikesRepository.existsByPost_IdAndUser_UserId(postId, userId)) {
            postLikesRepository.deleteByPost_IdAndUser_UserId(postId, userId);
            stats.decLike();
            liked = false;
        } else {
            //좋아요 증가 이때 좋아요 개수 3개 이상시 포인트 제공 : 100P
            postLikesRepository.save(PostLikes.of(post, user));
            stats.incLike();
            liked = true;

            // 좋아요 3개 이상 “첫 1회” 보상 -> 정보글 한정
            if (stats.getLikeCount() >= 3 && stats.tryMarkLikeRewarded3()
                    && post.getBoard().getCode() == BoardCode.INFO) {
                // 작성자에게 지급 (본인 글이면 지급 안 줄지 정책 결정)
                Long authorId = post.getUser().getUserId();
                pointService.earnPoint(authorId, rewardFirstThreeLikes, PointEvent.threeLikeReward(authorId, postId));
            }
        }
        return new ToggleLikeResponse(liked, stats.getLikeCount());
    }

    @Transactional
    @Override
    public PostDetailResponse getDetail(Long userId, Long postId) {
        if (userId == null) throw new CustomException(AuthErrorCode.INVALID_TOKEN);

        Posts post = postsRepository.findByIdForRead(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));

        requirePublished(post);

        if (postStatsRepository.incrementView(postId, LocalDateTime.now()) == 0) {
            throw new CustomException(CommunityErrorCode.POST_STATS_NOT_FOUND);
        }
        PostStats stats = postStatsRepository.findByPost_Id(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_STATS_NOT_FOUND));

        boolean likedByMe = postLikesRepository.existsByPost_IdAndUser_UserId(postId, userId);

        List<Long> tagIds = postTagsRepository.findByPost_Id(postId).stream()
                .map(pt -> pt.getTag().getId())
                .toList();

        Optional<AcceptedComments> acceptedOpt = acceptedCommentsRepository.findByPost_Id(postId);
        Long acceptedCommentId = acceptedOpt
                .map(ac -> ac.getComment().getId())
                .orElse(null);

        ///  접근권한 관련 설정파트
        boolean payRequired = post.getBoard().getCode() == BoardCode.QUESTION && acceptedOpt.isPresent();

        ContentAccessStatus accessStatus;
        Integer requiredPoints = null;
        Integer myPoints = null;

        if (payRequired) {
            // 작성자 무료
            if (Objects.equals(userId, post.getUser().getUserId())) {
                accessStatus = ContentAccessStatus.GRANTED;
                requiredPoints = questionViewCost;
            } else if (postAccessRepository.existsByPost_IdAndUser_UserId(postId, userId)) {
                accessStatus = ContentAccessStatus.GRANTED;
                requiredPoints = questionViewCost;
            } else {
                myPoints = pointService.getBalance(userId);
                requiredPoints = questionViewCost;
                accessStatus = (myPoints >= questionViewCost)
                        ? ContentAccessStatus.NEED_PURCHASE
                        : ContentAccessStatus.INSUFFICIENT_POINTS;
            }
        } else {
            accessStatus = ContentAccessStatus.GRANTED;
        }
        String content = accessStatus.canReadProtectedContent() ? post.getContent() : null;

        /// 글쓴이 프로필
        AuthorDto author = post.isAnonymous()
                ? null
                : authorAssembler
                    .buildAuthorMap(List.of(post.getUser().getUserId()))
                    .get(post.getUser().getUserId());

        /// 첨부파일 내려주는 파트
        List<PostAttachmentItemResponse> attachments = null;
        if (accessStatus.canReadProtectedContent()) {
            List<PostAttachments> atts = postAttachmentsRepository
                    .findByPost_IdAndStatusTrueOrderBySortOrderAscIdAsc(postId);

            // fileKey 있는 것만 대상으로
            List<PostAttachments> validAtts = atts.stream()
                    .filter(a -> StringUtils.hasText(a.getFileKey()))
                    .toList();

            // key 목록
            List<String> keys = validAtts.stream()
                    .map(PostAttachments::getFileKey)
                    .distinct()
                    .toList();

            // ticket bulk
            Map<String, UploadTicket> ticketMap = keys.isEmpty()
                    ? Map.of()
                    : uploadTicketRepository.findAllByStorageKeyIn(keys).stream()
                    .collect(Collectors.toMap(
                            UploadTicket::getStorageKey,
                            t -> t,
                            (a, b) -> a
                    ));

            // presign (loop)
            Map<String, String> urlMap = new HashMap<>();
            for (String key : keys) {
                UploadTicket t = ticketMap.get(key);
                String filename = (t == null) ? null : t.getOriginalFilename();
                String contentType = (t == null) ? null : t.getContentType();

                try {
                    String url = presignEngine.presignDownload(key, filename, contentType).downloadUrl();
                    if (StringUtils.hasText(url)) {
                        urlMap.put(key, url);
                    }
                } catch (Exception e) {
                    log.warn("presignDownload failed. postId={}, key={}", postId, key, e);
                }
            }

            attachments = validAtts.stream()
                    .filter(a -> StringUtils.hasText(urlMap.get(a.getFileKey())))
                    .map(a -> new PostAttachmentItemResponse(
                            a.getId(),
                            a.getSortOrder(),
                            a.getFileKey(),
                            urlMap.get(a.getFileKey()),
                            a.getWidth(),
                            a.getHeight(),
                            a.getFileSize()
                    ))
                    .toList();
        }
        boolean exists = postBookmarksRepository.existsByPost_IdAndUser_UserId(postId, userId);

        return new PostDetailResponse(
                post.getId(),
                post.getBoard().getCode(),
                post.getTitle(),
                content,
                post.isAnonymous(),
                stats.getViewCount(),
                stats.getBookmarkCount(),
                stats.getLikeCount(),
                likedByMe,
                acceptedCommentId,
                tagIds,
                attachments,
                accessStatus,
                exists,
                requiredPoints,
                myPoints,
                author
        );
    }

    @Transactional
    @Override
    public void acceptComment(Long userId, Long postId, Long commentId) {
        if (userId == null) throw new CustomException(AuthErrorCode.INVALID_TOKEN);

        Posts post = postsRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));

        requirePublished(post);

        if (post.getBoard().getCode() != BoardCode.QUESTION) {
            throw new CustomException(CommunityErrorCode.ONLY_QUESTION_CAN_ACCEPT);
        }
        if (!Objects.equals(post.getUser().getUserId(), userId)) {
            throw new CustomException(CommunityErrorCode.POST_FORBIDDEN);
        }

        Comments comment = commentsRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.COMMENT_NOT_FOUND));

        if (!Objects.equals(comment.getPost().getId(), postId)) {
            throw new CustomException(CommunityErrorCode.COMMENT_NOT_IN_POST);
        }
        if (!comment.getStatus().isPublished()) {
            throw new CustomException(CommunityErrorCode.CANNOT_ACCEPT_UNPUBLISHED_COMMENT);
        }
        if (Objects.equals(comment.getUserId(), userId)) {
            throw new CustomException(CommunityErrorCode.CANNOT_ACCEPT_OWN_COMMENT);
        }

        if (acceptedCommentsRepository.existsByPost_Id(postId)) {
            throw new CustomException(CommunityErrorCode.ALREADY_ACCEPTED);
        }

        try {
            acceptedCommentsRepository.saveAndFlush(AcceptedComments.of(post, comment, userId));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(CommunityErrorCode.ALREADY_ACCEPTED, e);
        }

        touchStats(postId);

        Long receiverId = comment.getUserId();
        if (receiverId != null && !Objects.equals(receiverId, userId)) {
            pointService.earnPoint(receiverId, rewardAcceptedComment, PointEvent.commentSelection(postId, commentId));
            eventPublisher.publishEvent(SimpleNotifiableEvent.of(
                    receiverId,
                    post.isAnonymous() ? null : userId,
                    NotificationType.COMMENT_ACCEPTED,
                    "작성하신 댓글이 채택되었습니다.",
                    postId,
                    commentId
            ));
        }
    }

    @Transactional
    @Override
    public ToggleBookmarkResponse toggleBookmark(Long userId, Long postId) {
        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));

        Posts post = postsRepository.findByIdForRead(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));

        requirePublished(post);

        PostStats stats = postStatsRepository.findByPostIdForUpdate(postId)
                .orElseGet(() -> postStatsRepository.save(PostStats.init(post)));

        boolean exists = postBookmarksRepository.existsByPost_IdAndUser_UserId(postId, userId);

        if (exists) {
            postBookmarksRepository.deleteByPost_IdAndUser_UserId(postId, userId);
            stats.decBookmark();
        } else {
            postBookmarksRepository.save(PostBookmarks.create(post, user));
            stats.incBookmark();
        }

        postStatsRepository.save(stats);

        return new ToggleBookmarkResponse(postId, !exists, stats.getBookmarkCount());
    }


    @Transactional
    @Override
    public PurchasePostAccessResponse purchasePostAccess(Long userId, Long postId) {
        userRepository.lockUserRow(userId);

        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));

        Posts post = postsRepository.findById(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));

        if (!post.getStatus().isPublished()) {
            throw new CustomException(CommunityErrorCode.POST_NOT_PUBLISHED);
        }

        boolean isQuestion = post.getBoard().getCode() == BoardCode.QUESTION;

        if (!isQuestion && post.getAccessType() != PostAccessType.POINT_REQUIRED) {
            int bal = pointService.getBalance(user.getUserId());
            return new PurchasePostAccessResponse(postId, ContentAccessStatus.GRANTED, bal, true);
        }

        // 2) 작성자는 무료
        if (userId.equals(post.getUser().getUserId())) {
            int bal = pointService.getBalance(user.getUserId());
            return new PurchasePostAccessResponse(postId, ContentAccessStatus.GRANTED, bal, true);
        }

        // 3) 이미 구매했으면 무료
        if (postAccessRepository.existsByPost_IdAndUser_UserId(postId, userId)) {
            int bal = pointService.getBalance(user.getUserId());
            return new PurchasePostAccessResponse(postId, ContentAccessStatus.GRANTED, bal, true);
        }

        pointService.spendPoint(userId, questionViewCost, PointEvent.postAccess(userId, postId));

        try {
            postAccessRepository.save(PostAccess.of(user, post, questionViewCost));
        } catch (DataIntegrityViolationException ignored) {
        }

        int remaining = pointService.getBalance(userId);
        return new PurchasePostAccessResponse(postId, ContentAccessStatus.GRANTED, remaining, false);
    }

    private void replaceTags(Posts post, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return;

        List<Long> ids = normalizeTagIds(tagIds);
        if (ids.isEmpty()) return;

        List<Tag> tags = tagRepository.findAllById(ids);
        if (tags.size() != ids.size()) throw new CustomException(CommunityErrorCode.INVALID_TAG_IDS);

        for (Tag t : tags) if (!t.isActive()) throw new CustomException(CommunityErrorCode.INACTIVE_TAG);

        List<PostTags> links = tags.stream()
                .map(t -> PostTags.link(post, t))
                .toList();

        postTagsRepository.saveAll(links);
    }

    private List<Long> normalizeTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return List.of();

        if (tagIds.size() > CamNecT.server.domain.community.dto.request.CommunityRequestLimits.MAX_TAGS_PER_POST
                || tagIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new CustomException(CommunityErrorCode.INVALID_TAG_IDS);
        }

        List<Long> normalized = tagIds.stream()
                .map(id -> id == LEGACY_TAG_ID ? CANONICAL_TAG_ID : id)
                .toList();

        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw new CustomException(CommunityErrorCode.INVALID_TAG_IDS);
        }

        return normalized;
    }

    private void touchStats(Long postId) {
        postStatsRepository.touchByPostId(postId, LocalDateTime.now());
    }

    private void requirePublished(Posts post) {
        if (!post.getStatus().isPublished()) {
            throw new CustomException(CommunityErrorCode.POST_NOT_PUBLISHED);
        }
    }
}
