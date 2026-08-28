package CamNecT.server.domain.profile.components.archive.service;

import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityBookmarkRepository;
import CamNecT.server.domain.activity.repository.recruitment.RecruitmentBookmarkRepository;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import CamNecT.server.domain.profile.components.archive.dto.response.MyArchiveResponse;
import CamNecT.server.domain.profile.components.ProfileComponentAccessGuard;
import CamNecT.server.domain.profile.components.archive.service.util.CommunityArchiveAssembler;
import CamNecT.server.domain.profile.components.archive.service.util.ExternalArchiveAssembler;
import CamNecT.server.domain.profile.components.archive.service.util.RecruitmentArchiveAssembler;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveQueryService {

    private final PostsRepository postsRepository;
    private final CommunityArchiveAssembler communityArchiveAssembler;

    private final ExternalActivityBookmarkRepository externalActivityBookmarkRepository;
    private final ExternalArchiveAssembler externalArchiveAssembler;

    private final RecruitmentBookmarkRepository recruitmentBookmarkRepository;
    private final RecruitmentArchiveAssembler recruitmentArchiveAssembler;
    private final ProfileComponentAccessGuard accessGuard;

    public MyArchiveResponse getCommunityArchive(Long userId, MyArchiveResponse.ArchiveKind kind, MyArchiveResponse.Sort sort,
                                                 Long cursorId, Long cursorValue, int size) {
        Users viewer = accessGuard.requireAuthenticatedUser(userId);
        return communityArchive(kind, userId, viewer.getRole() == UserRole.ADMIN,
                sort, cursorId, cursorValue, size);
    }
    public MyArchiveResponse getExternalArchive(Long userId, MyArchiveResponse.ArchiveKind kind, MyArchiveResponse.Sort sort,
                                                Long cursorId, Long cursorValue, int size) {
        accessGuard.requireAuthenticatedUser(userId);
        return externalArchive(kind, userId, sort, cursorId, cursorValue, size);
    }

    public MyArchiveResponse getRecruitmentArchive(Long userId, MyArchiveResponse.ArchiveKind kind, MyArchiveResponse.Sort sort,
                                                   Long cursorId, Long cursorValue, int size) {
        accessGuard.requireAuthenticatedUser(userId);
        return recruitmentArchive(kind, userId, sort, cursorId, cursorValue, size);
    }

    private MyArchiveResponse communityArchive(MyArchiveResponse.ArchiveKind kind,
                                               Long userId,
                                               boolean adminRead,
                                               MyArchiveResponse.Sort sort,
                                               Long cursorId,
                                               Long cursorValue,
                                               int size) {

        Pageable pageable = PageRequest.of(0, size);

        Slice<Posts> slice = switch (kind) {
            case MY_POSTS -> (sort == MyArchiveResponse.Sort.LATEST)
                    ? postsRepository.findMyPostsLatest(userId, PostStatus.PUBLISHED, cursorId, pageable)
                    : postsRepository.findMyPostsRecommended(userId, PostStatus.PUBLISHED, cursorValue, cursorId, pageable);

            case BOOKMARKS -> (sort == MyArchiveResponse.Sort.LATEST)
                    ? postsRepository.findBookmarkedPostsLatest(userId, PostStatus.PUBLISHED, cursorId, pageable)
                    : postsRepository.findBookmarkedPostsRecommended(userId, PostStatus.PUBLISHED, cursorValue, cursorId, pageable);
        };

        List<Posts> posts = slice.getContent();
        if (posts.isEmpty()) {
            return new MyArchiveResponse(MyArchiveResponse.Tab.COMMUNITY, sort, List.of(), slice.hasNext(), null, null);
        }

        var assembled = communityArchiveAssembler.assemble(userId, adminRead, posts);
        Posts last = posts.getLast();

        Long nextCursorValue = (sort == MyArchiveResponse.Sort.RECOMMENDED) ? assembled.nextHotScore() : null;

        return new MyArchiveResponse(
                MyArchiveResponse.Tab.COMMUNITY,
                sort,
                assembled.items(),
                slice.hasNext(),
                last.getId(),
                nextCursorValue
        );
    }

    private MyArchiveResponse externalArchive(MyArchiveResponse.ArchiveKind kind,
                                              Long userId,
                                              MyArchiveResponse.Sort sort,
                                              Long cursorId,
                                              Long cursorValue,
                                              int size) {

        Pageable pageable = PageRequest.of(0, size);

        Slice<Object[]> slice = switch (kind) {
            case BOOKMARKS -> (sort == MyArchiveResponse.Sort.LATEST)
                    ? externalActivityBookmarkRepository.findBookmarkedActivitiesLatestWithCounts(userId, cursorId, pageable)
                    : externalActivityBookmarkRepository.findBookmarkedActivitiesRecommendedWithCounts(userId, cursorValue, cursorId, pageable);

            case MY_POSTS -> (sort == MyArchiveResponse.Sort.LATEST)
                    ? externalActivityBookmarkRepository.findMyActivitiesLatestWithCounts(userId, cursorId, pageable)
                    : externalActivityBookmarkRepository.findMyActivitiesRecommendedWithCounts(userId, cursorValue, cursorId, pageable);
        };

        List<Object[]> rows = slice.getContent();
        if (rows.isEmpty()) {
            return new MyArchiveResponse(MyArchiveResponse.Tab.EXTERNAL, sort, List.of(), slice.hasNext(), null, null);
        }

        var assembled = externalArchiveAssembler.assemble(rows);

        Long nextCursorValue = (sort == MyArchiveResponse.Sort.RECOMMENDED)
                ? assembled.lastCursorValue()
                : null;

        return new MyArchiveResponse(
                MyArchiveResponse.Tab.EXTERNAL,
                sort,
                assembled.items(),
                slice.hasNext(),
                assembled.lastId(),
                nextCursorValue
        );
    }

    private MyArchiveResponse recruitmentArchive(MyArchiveResponse.ArchiveKind kind,
                                                 Long userId,
                                                 MyArchiveResponse.Sort sort,
                                                 Long cursorId,
                                                 Long cursorValue,
                                                 int size) {

        Pageable pageable = PageRequest.of(0, size);

        Slice<Object[]> slice = switch (kind) {
            case BOOKMARKS -> (sort == MyArchiveResponse.Sort.LATEST)
                    ? recruitmentBookmarkRepository.findBookmarkedRecruitmentsLatestWithCounts(userId, cursorId, pageable)
                    : recruitmentBookmarkRepository.findBookmarkedRecruitmentsRecommendedWithCounts(userId, cursorValue, cursorId, pageable);

            case MY_POSTS -> (sort == MyArchiveResponse.Sort.LATEST)
                    ? recruitmentBookmarkRepository.findMyRecruitmentsLatestWithCounts(userId, cursorId, pageable)
                    : recruitmentBookmarkRepository.findMyRecruitmentsRecommendedWithCounts(userId, cursorValue, cursorId, pageable);
        };

        List<Object[]> rows = slice.getContent();
        if (rows.isEmpty()) {
            return new MyArchiveResponse(MyArchiveResponse.Tab.RECRUITMENT, sort, List.of(), slice.hasNext(), null, null);
        }

        var assembled = recruitmentArchiveAssembler.assemble(rows);

        Long nextCursorValue = (sort == MyArchiveResponse.Sort.RECOMMENDED)
                ? assembled.lastCursorValue()
                : null;

        return new MyArchiveResponse(
                MyArchiveResponse.Tab.RECRUITMENT,
                sort,
                assembled.items(),
                slice.hasNext(),
                assembled.lastId(),
                nextCursorValue
        );
    }
}
