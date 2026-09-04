package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.dto.response.PostListResponse;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.PostAccessType;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.community.repository.Posts.*;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static CamNecT.server.domain.community.dto.request.CommunityRequestLimits.MAX_TAG_FILTERS;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostQueryServiceImpl implements PostQueryService {
    private final PostsRepository postsRepository;
    private final UserRepository userRepository;

    private final PostSummaryAssembler postSummaryAssembler;

    @Override
    public PostListResponse getPosts(Long userId, Tab tab, Sort sort, List<Long> tagIds, String keyword,
                                     Long cursorId, Long cursorValue, int size) {
        int limit = Math.clamp(size, 1, 50);

        BoardCode code = toBoardCode(tab);
        String kw = normalizeKeyword(keyword);
        TagFilter tagFilter = normalizeTagFilter(tagIds);
        validateCursor(sort, cursorId, cursorValue);
        boolean adminRead = isAdmin(userId);

        Slice<Posts> slice = switch (sort) {
            case LATEST -> postsRepository.findFeedLatestWithFilter(
                    PostStatus.PUBLISHED, code, tagFilter.tagIds(), tagFilter.enabled(), kw,
                    userId, adminRead, PostAccessType.POINT_REQUIRED, BoardCode.QUESTION,
                    cursorId, PageRequest.of(0, limit)
            );
            case RECOMMENDED -> postsRepository.findFeedRecommended(
                    PostStatus.PUBLISHED, code, tagFilter.tagIds(), tagFilter.enabled(), kw,
                    userId, adminRead, PostAccessType.POINT_REQUIRED, BoardCode.QUESTION,
                    cursorValue, cursorId, PageRequest.of(0, limit)
            );
            case LIKE -> postsRepository.findFeedLikeDesc(
                    PostStatus.PUBLISHED, code, tagFilter.tagIds(), tagFilter.enabled(), kw,
                    userId, adminRead, PostAccessType.POINT_REQUIRED, BoardCode.QUESTION,
                    cursorValue, cursorId, PageRequest.of(0, limit)
            );
            case BOOKMARK -> postsRepository.findFeedBookmarkDesc(
                    PostStatus.PUBLISHED, code, tagFilter.tagIds(), tagFilter.enabled(), kw,
                    userId, adminRead, PostAccessType.POINT_REQUIRED, BoardCode.QUESTION,
                    cursorValue, cursorId, PageRequest.of(0, limit)
            );
        };

        return mapToListResponse(userId, adminRead, slice, sort);
    }

    @Override
    public PostListResponse getPostsByTag(Long userId, Long tagId, Long cursorValue, Long cursorId, int size) {
        int limit = Math.clamp(size, 1, 50);
        validateCursor(Sort.RECOMMENDED, cursorId, cursorValue);
        boolean adminRead = isAdmin(userId);

        Slice<Posts> slice = postsRepository.findFeedRecommended(
                PostStatus.PUBLISHED,
                null,          // board filter 없음
                List.of(tagId),
                true,
                null,          // keyword 없음
                userId,
                adminRead,
                PostAccessType.POINT_REQUIRED,
                BoardCode.QUESTION,
                cursorValue,
                cursorId,
                PageRequest.of(0, limit)
        );

        return mapToListResponse(userId, adminRead, slice, Sort.RECOMMENDED);
    }

    @Override
    public PostListResponse getWaitingQuestions(Long userId,int size) {
        boolean adminRead = isAdmin(userId);
        Slice<Posts> slice = postsRepository.findWaitingQuestions(
                PostStatus.PUBLISHED,
                BoardCode.QUESTION,
                PageRequest.of(0, size)
        );
        return mapToListResponse(userId, adminRead, slice, Sort.LATEST);
    }

    private PostListResponse mapToListResponse(Long userId, boolean adminRead, Slice<Posts> slice, Sort sort) {
        List<Posts> posts = slice.getContent();
        if (posts.isEmpty()) return PostListResponse.of(List.of(), slice.hasNext(), null);

        var res = postSummaryAssembler.assemble(userId, adminRead, posts);

        Long nextCursorValue = switch (sort) {
            case LATEST -> null;
            case RECOMMENDED -> res.cursorStats().hotScore();
            case LIKE -> res.cursorStats().likeCount();
            case BOOKMARK -> res.cursorStats().bookmarkCount();
        };

        return PostListResponse.of(res.items(), slice.hasNext(), nextCursorValue);
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) return null;
        String t = keyword.trim();
        if (t.isBlank()) return null;
        if (t.length() > CamNecT.server.domain.community.dto.request.CommunityRequestLimits.MAX_SEARCH_KEYWORD_LENGTH
                || t.chars().anyMatch(Character::isISOControl)) {
            throw new CustomException(CommunityErrorCode.INVALID_SEARCH_KEYWORD);
        }
        return t.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private static TagFilter normalizeTagFilter(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new TagFilter(false, List.of(-1L));
        }
        if (tagIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new CustomException(CommunityErrorCode.INVALID_TAG_IDS);
        }

        List<Long> normalized = tagIds.stream().distinct().toList();
        if (normalized.size() > MAX_TAG_FILTERS) {
            throw new CustomException(CommunityErrorCode.INVALID_TAG_IDS);
        }
        return new TagFilter(true, normalized);
    }

    private static void validateCursor(Sort sort, Long cursorId, Long cursorValue) {
        if (cursorId != null && cursorId <= 0) {
            throw new CustomException(CommunityErrorCode.INVALID_CURSOR);
        }
        if (cursorValue != null && cursorValue < 0) {
            throw new CustomException(CommunityErrorCode.INVALID_CURSOR);
        }

        if (sort == Sort.LATEST) {
            if (cursorValue != null) {
                throw new CustomException(CommunityErrorCode.INVALID_CURSOR);
            }
            return;
        }

        if ((cursorId == null) != (cursorValue == null)) {
            throw new CustomException(CommunityErrorCode.INVALID_CURSOR);
        }
    }

    private static BoardCode toBoardCode(Tab tab) {
        return switch (tab) {
            case ALL -> null;
            case INFO -> BoardCode.INFO;
            case QUESTION -> BoardCode.QUESTION;
        };
    }

    private boolean isAdmin(Long userId) {
        return userId != null && userRepository.existsByUserIdAndRole(userId, UserRole.ADMIN);
    }

    private record TagFilter(boolean enabled, List<Long> tagIds) {
    }
}
