package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostQueryServiceImplTest {

    @Mock PostsRepository postsRepository;
    @Mock PostSummaryAssembler postSummaryAssembler;

    @InjectMocks PostQueryServiceImpl service;

    @Test
    void searchEscapesLikeWildcardsBeforeRepositoryCall() {
        when(postsRepository.findFeedLatestWithFilter(
                eq(PostStatus.PUBLISHED), eq(BoardCode.INFO), isNull(), anyString(), isNull(), any(Pageable.class)
        )).thenReturn(new SliceImpl<>(List.of()));

        service.getPosts(
                1L, PostQueryService.Tab.INFO, PostQueryService.Sort.LATEST,
                null, "%_!", null, null, 20
        );

        verify(postsRepository).findFeedLatestWithFilter(
                eq(PostStatus.PUBLISHED), eq(BoardCode.INFO), isNull(), eq("!%!_!!"), isNull(), any(Pageable.class)
        );
    }
}
