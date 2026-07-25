package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.dto.response.PostListResponse;
import CamNecT.server.domain.community.model.Posts.PostStats;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.community.repository.Posts.*;
import CamNecT.server.domain.community.repository.Posts.PostStatsRepository;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostQueryServiceImpl implements PostQueryService {
    private final PostsRepository postsRepository;
    private final PostStatsRepository postStatsRepository;

    private final PostSummaryAssembler postSummaryAssembler;

    @Override
    public PostListResponse getPosts(Long userId, Tab tab, Sort sort, Long tagId, String keyword,
                                     Long cursorId, Long cursorValue, int size) {
        int limit = Math.clamp(size, 1, 50);

        BoardCode code = toBoardCode(tab);
        String kw = normalizeKeyword(keyword);

        Long cv = cursorValue;
        if (cv == null && cursorId != null && sort != Sort.LATEST) {
            if (!postsRepository.existsById(cursorId)) {
                throw new CustomException(CommunityErrorCode.INVALID_CURSOR);
            }

            PostStats ps = postStatsRepository.findByPost_Id(cursorId)
                    .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_STATS_NOT_FOUND));

            cv = switch (sort) {
                case RECOMMENDED -> ps.getHotScore();
                case LIKE -> ps.getLikeCount();
                case BOOKMARK -> ps.getBookmarkCount();
                default -> throw new CustomException(ErrorCode.INTERNAL_ERROR);
            };
        }

        Slice<Posts> slice = switch (sort) {
            case LATEST -> postsRepository.findFeedLatestWithFilter(
                    PostStatus.PUBLISHED, code, tagId, kw, cursorId, PageRequest.of(0, limit)
            );
            case RECOMMENDED -> postsRepository.findFeedRecommended(
                    PostStatus.PUBLISHED, code, tagId, kw, cv, cursorId, PageRequest.of(0, limit)
            );
            case LIKE -> postsRepository.findFeedLikeDesc(
                    PostStatus.PUBLISHED, code, tagId, kw, cv, cursorId, PageRequest.of(0, limit)
            );
            case BOOKMARK -> postsRepository.findFeedBookmarkDesc(
                    PostStatus.PUBLISHED, code, tagId, kw, cv, cursorId, PageRequest.of(0, limit)
            );
        };

        return mapToListResponse(userId, slice, sort);
    }

    @Override
    public PostListResponse getPostsByTag(Long userId, Long tagId, Long cursorValue, Long cursorId, int size) {
        int limit = Math.clamp(size, 1, 50);

        Slice<Posts> slice = postsRepository.findFeedRecommended(
                PostStatus.PUBLISHED,
                null,          // board filter 없음
                tagId,
                null,          // keyword 없음
                cursorValue,
                cursorId,
                PageRequest.of(0, limit)
        );

        return mapToListResponse(userId, slice, Sort.RECOMMENDED);
    }

    @Override
    public PostListResponse getWaitingQuestions(Long userId,int size) {
        Slice<Posts> slice = postsRepository.findWaitingQuestions(
                PostStatus.PUBLISHED,
                BoardCode.QUESTION,
                PageRequest.of(0, size)
        );
        return mapToListResponse(userId, slice, Sort.LATEST);
    }

    private PostListResponse mapToListResponse(Long userId, Slice<Posts> slice, Sort sort) {
        List<Posts> posts = slice.getContent();
        if (posts.isEmpty()) return PostListResponse.of(List.of(), slice.hasNext(), null);

        var res = postSummaryAssembler.assemble(userId, posts);

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

    private static BoardCode toBoardCode(Tab tab) {
        return switch (tab) {
            case ALL -> null;
            case INFO -> BoardCode.INFO;
            case QUESTION -> BoardCode.QUESTION;
        };
    }
}
