package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.PostAccessType;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostQueryServiceImplTest {

    @Mock PostsRepository postsRepository;
    @Mock PostSummaryAssembler postSummaryAssembler;

    @InjectMocks PostQueryServiceImpl service;

    @Test
    void latestRejectsCursorValue() {
        CustomException exception = assertThrows(CustomException.class, () -> service.getPosts(
                1L, PostQueryService.Tab.ALL, PostQueryService.Sort.LATEST,
                null, null, 10L, 3L, 20
        ));

        assertThat(exception.getErrorCode()).isEqualTo(CommunityErrorCode.INVALID_CURSOR);
        verifyNoInteractions(postsRepository);
    }

    @Test
    void rankedSortRequiresCursorIdAndValueTogether() {
        for (PostQueryService.Sort sort : List.of(
                PostQueryService.Sort.RECOMMENDED,
                PostQueryService.Sort.LIKE,
                PostQueryService.Sort.BOOKMARK
        )) {
            CustomException idOnly = assertThrows(CustomException.class, () -> service.getPosts(
                    1L, PostQueryService.Tab.ALL, sort, null, null, 10L, null, 20
            ));
            CustomException valueOnly = assertThrows(CustomException.class, () -> service.getPosts(
                    1L, PostQueryService.Tab.ALL, sort, null, null, null, 3L, 20
            ));

            assertThat(idOnly.getErrorCode()).isEqualTo(CommunityErrorCode.INVALID_CURSOR);
            assertThat(valueOnly.getErrorCode()).isEqualTo(CommunityErrorCode.INVALID_CURSOR);
        }
        verifyNoInteractions(postsRepository);
    }

    @Test
    void recommendedAcceptsCompleteCursorPair() {
        when(postsRepository.findFeedRecommended(
                eq(PostStatus.PUBLISHED), isNull(), isNull(), isNull(),
                eq(1L), eq(PostAccessType.POINT_REQUIRED), eq(BoardCode.QUESTION),
                eq(3L), eq(10L), any(Pageable.class)
        )).thenReturn(new SliceImpl<>(List.of()));

        assertDoesNotThrow(() -> service.getPosts(
                1L, PostQueryService.Tab.ALL, PostQueryService.Sort.RECOMMENDED,
                null, null, 10L, 3L, 20
        ));
    }

    @Test
    void searchEscapesLikeWildcardsBeforeRepositoryCall() {
        when(postsRepository.findFeedLatestWithFilter(
                eq(PostStatus.PUBLISHED), eq(BoardCode.INFO), isNull(), anyString(),
                eq(1L), eq(PostAccessType.POINT_REQUIRED), eq(BoardCode.QUESTION),
                isNull(), any(Pageable.class)
        )).thenReturn(new SliceImpl<>(List.of()));

        service.getPosts(
                1L, PostQueryService.Tab.INFO, PostQueryService.Sort.LATEST,
                null, "%_!", null, null, 20
        );

        verify(postsRepository).findFeedLatestWithFilter(
                eq(PostStatus.PUBLISHED), eq(BoardCode.INFO), isNull(), eq("!%!_!!"),
                eq(1L), eq(PostAccessType.POINT_REQUIRED), eq(BoardCode.QUESTION),
                isNull(), any(Pageable.class)
        );
    }
}
