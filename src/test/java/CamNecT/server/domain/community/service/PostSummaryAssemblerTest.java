package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.dto.response.PostSummaryResponse;
import CamNecT.server.domain.community.model.Boards;
import CamNecT.server.domain.community.model.Posts.PostAttachments;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.PostAccessType;
import CamNecT.server.domain.community.repository.Comments.AcceptedCommentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostAccessRepository;
import CamNecT.server.domain.community.repository.Posts.PostAttachmentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostStatsRepository;
import CamNecT.server.domain.community.repository.Posts.PostTagsRepository;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.point.service.PointService;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostSummaryAssemblerTest {

    @Mock PostStatsRepository postStatsRepository;
    @Mock PostAttachmentsRepository postAttachmentsRepository;
    @Mock PostTagsRepository postTagsRepository;
    @Mock PostAccessRepository postAccessRepository;
    @Mock AcceptedCommentsRepository acceptedCommentsRepository;
    @Mock PointService pointService;
    @Mock PublicUrlIssuer publicUrlIssuer;
    @Mock AuthorAssembler authorAssembler;

    @InjectMocks PostSummaryAssembler assembler;

    @Test
    void anonymousPostKeepsContentButOmitsAuthorProfile() {
        Users user = Users.builder().userId(1L).name("작성자").build();
        Posts post = Posts.builder()
                .id(10L)
                .board(Boards.of(BoardCode.INFO, "정보"))
                .user(user)
                .title("익명 글")
                .content("본문은 정상적으로 노출된다")
                .isAnonymous(true)
                .status(CamNecT.server.domain.community.model.enums.PostStatus.PUBLISHED)
                .build();

        when(postStatsRepository.findByPost_IdIn(List.of(10L))).thenReturn(List.of());
        when(postTagsRepository.findAllByPostIdsWithTag(List.of(10L))).thenReturn(List.of());
        when(acceptedCommentsRepository.findAcceptedPostIds(List.of(10L))).thenReturn(List.of());
        when(postAttachmentsRepository.findThumbCandidates(List.of(10L))).thenReturn(List.of());
        when(authorAssembler.buildAuthorMap(List.of())).thenReturn(Map.of());

        PostSummaryResponse result = assembler.assemble(2L, List.of(post)).items().getFirst();

        assertThat(result.preview()).isEqualTo("본문은 정상적으로 노출된다");
        assertThat(result.author()).isNull();
    }

    @Test
    void grantedPaywalledPostIncludesThumbnail() {
        Users user = Users.builder().userId(1L).name("작성자").build();
        Posts post = Posts.builder()
                .id(10L)
                .board(Boards.of(BoardCode.QUESTION, "질문"))
                .user(user)
                .title("질문")
                .content("본문")
                .status(CamNecT.server.domain.community.model.enums.PostStatus.PUBLISHED)
                .accessType(PostAccessType.POINT_REQUIRED)
                .build();
        PostAttachments thumbnail = PostAttachments.builder()
                .id(100L)
                .post(post)
                .fileKey("community/thumb.png")
                .status(true)
                .sortOrder(0)
                .build();

        when(postStatsRepository.findByPost_IdIn(List.of(10L))).thenReturn(List.of());
        when(postTagsRepository.findAllByPostIdsWithTag(List.of(10L))).thenReturn(List.of());
        when(acceptedCommentsRepository.findAcceptedPostIds(List.of(10L))).thenReturn(List.of());
        when(postAttachmentsRepository.findThumbCandidates(List.of(10L))).thenReturn(List.of(thumbnail));
        when(authorAssembler.buildAuthorMap(List.of(1L))).thenReturn(Map.of());
        when(publicUrlIssuer.issueImagePublicUrl("community/thumb.png")).thenReturn("https://cdn/thumb.png");

        PostSummaryResponse result = assembler.assemble(1L, List.of(post)).items().getFirst();

        assertThat(result.accessStatus()).isEqualTo(CamNecT.server.domain.community.model.enums.ContentAccessStatus.GRANTED);
        assertThat(result.thumbnailUrl()).isEqualTo("https://cdn/thumb.png");
    }
}
