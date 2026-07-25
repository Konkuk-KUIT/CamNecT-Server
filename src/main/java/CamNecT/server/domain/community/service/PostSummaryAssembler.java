package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.dto.AuthorDto;
import CamNecT.server.domain.community.dto.response.PostSummaryResponse;
import CamNecT.server.domain.community.model.Posts.PostStats;
import CamNecT.server.domain.community.model.Posts.PostTags;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.ContentAccessStatus;
import CamNecT.server.domain.community.model.enums.PostAccessType;
import CamNecT.server.domain.community.repository.Comments.AcceptedCommentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostAccessRepository;
import CamNecT.server.domain.community.repository.Posts.PostAttachmentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostStatsRepository;
import CamNecT.server.domain.community.repository.Posts.PostTagsRepository;
import CamNecT.server.global.point.service.PointService;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostSummaryAssembler {

    private static final int MAX_CONTENT = 80;

    private final PostStatsRepository postStatsRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final PostTagsRepository postTagsRepository;
    private final PostAccessRepository postAccessRepository;
    private final AcceptedCommentsRepository acceptedCommentsRepository;

    private final PointService pointService;
    private final PublicUrlIssuer publicUrlIssuer;
    private final AuthorAssembler authorAssembler;

    @Value("${app.point.cost.question-view:100}")
    private int questionViewCost;

    public AssembleResult assemble(Long userId, List<Posts> posts) {
        if (posts == null || posts.isEmpty()) return new AssembleResult(List.of(), new CursorStats(0L, 0L, 0L));

        List<Long> postIds = posts.stream().map(Posts::getId).toList();

        // stats bulk
        Map<Long, PostStats> statsMap = postStatsRepository.findByPost_IdIn(postIds).stream()
                .collect(Collectors.toMap(ps -> ps.getPost().getId(), ps -> ps, (a, b) -> a));

        // tags bulk
        Map<Long, List<String>> tagsMap = new HashMap<>();
        for (PostTags pt : postTagsRepository.findAllByPostIdsWithTag(postIds)) {
            Long pid = pt.getPost().getId();
            tagsMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(pt.getTag().getName());
        }

        // accepted bulk
        Set<Long> acceptedPostIds = new HashSet<>(acceptedCommentsRepository.findAcceptedPostIds(postIds));

        // thumbnail bulk (sortOrder=0)
        Map<Long, String> thumbKeyMap = new HashMap<>();
        postAttachmentsRepository.findThumbCandidates(postIds).forEach(a -> {
            Long pid = a.getPost().getId();
            if (!thumbKeyMap.containsKey(pid) && hasText(a.getFileKey())) {
                thumbKeyMap.put(pid, a.getFileKey());
            }
        });

        // author bulk
        List<Long> authorIds = posts.stream()
                .filter(p -> !p.isAnonymous())
                .map(p -> p.getUser().getUserId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, AuthorDto> authorMap = authorAssembler.buildAuthorMap(authorIds);

        // ===== access bulk 준비 =====
        List<Long> paywalledIds = posts.stream()
                .filter(p -> p.getAccessType() == PostAccessType.POINT_REQUIRED)
                .map(Posts::getId)
                .toList();

        Set<Long> grantedSet = (userId != null && !paywalledIds.isEmpty())
                ? new HashSet<>(postAccessRepository.findGrantedPostIds(userId, paywalledIds))
                : Set.of();

        Integer myPoints = null;
        boolean needBalance = (userId != null) && posts.stream().anyMatch(p ->
                p.getAccessType() == PostAccessType.POINT_REQUIRED
                        && !Objects.equals(userId, p.getUser().getUserId())
                        && !grantedSet.contains(p.getId())
        );
        if (needBalance) {
            myPoints = pointService.getBalance(userId);
        }
        // ===========================

        List<PostSummaryResponse> items = new ArrayList<>(posts.size());

        for (Posts p : posts) {
            PostStats ps = statsMap.get(p.getId());

            long likeCount = ps == null ? 0 : ps.getLikeCount();
            long commentCount = ps == null ? 0 : ps.getCommentCount();
            long answerCount = ps == null ? 0 : ps.getRootCommentCount();
            long bookmarkCount = ps == null ? 0 : ps.getBookmarkCount();

            // accessStatus 계산(원본 로직 그대로)
            ContentAccessStatus accessStatus;
            boolean paywalled = p.getAccessType() == PostAccessType.POINT_REQUIRED;

            if (!paywalled) {
                accessStatus = ContentAccessStatus.GRANTED;
            } else if (userId == null) {
                accessStatus = ContentAccessStatus.LOGIN_REQUIRED;
            } else if (Objects.equals(userId, p.getUser().getUserId()) || grantedSet.contains(p.getId())) {
                accessStatus = ContentAccessStatus.GRANTED;
            } else {
                int balance = (myPoints == null) ? 0 : myPoints;
                accessStatus = (balance >= questionViewCost)
                        ? ContentAccessStatus.NEED_PURCHASE
                        : ContentAccessStatus.INSUFFICIENT_POINTS;
            }

            String preview = accessStatus.canReadProtectedContent()
                    ? makePreview(p.getContent(), MAX_CONTENT)
                    : null;

            String thumbUrl = null;
            String thumbKey = thumbKeyMap.get(p.getId());
            if (accessStatus.canReadProtectedContent()) {
                thumbUrl = publicUrlIssuer.issueImagePublicUrl(thumbKey);
            }

            AuthorDto author = p.isAnonymous() ? null : authorMap.get(p.getUser().getUserId());

            items.add(new PostSummaryResponse(
                    p.getId(),
                    p.getBoard().getCode(),
                    p.getTitle(),
                    preview,
                    p.getCreatedAt(),
                    likeCount,
                    answerCount,
                    commentCount,
                    bookmarkCount,
                    acceptedPostIds.contains(p.getId()),
                    tagsMap.getOrDefault(p.getId(), List.of()),
                    author,
                    thumbUrl,
                    p.getAccessType(),
                    accessStatus
            ));
        }

        Posts last = posts.getLast();
        PostStats lastStats = statsMap.get(last.getId());

        long hot = (lastStats == null) ? 0L : lastStats.getHotScore();
        long like = (lastStats == null) ? 0L : lastStats.getLikeCount();
        long bm  = (lastStats == null) ? 0L : lastStats.getBookmarkCount();

        return new AssembleResult(items, new CursorStats(hot, like, bm));
    }

    public record AssembleResult(List<PostSummaryResponse> items, CursorStats cursorStats) {}
    public record CursorStats(long hotScore, long likeCount, long bookmarkCount) {}

    private static String makePreview(String content, int max) {
        if (content == null) return "";
        String t = content.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max) + "...";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
