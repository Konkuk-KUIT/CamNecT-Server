package CamNecT.server.domain.community.repository.Posts;

import CamNecT.server.domain.community.model.Boards;
import CamNecT.server.domain.community.model.Comments.AcceptedComments;
import CamNecT.server.domain.community.model.Comments.Comments;
import CamNecT.server.domain.community.model.Posts.PostAccess;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.PostAccessType;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class PostsRepositoryAccessSearchTest {

    @Autowired TestEntityManager entityManager;
    @Autowired PostsRepository postsRepository;

    @Test
    void protectedBodySearchIsVisibleOnlyToEntitledViewer() {
        Users owner = persistUser("owner");
        Users nonBuyer = persistUser("nonbuyer");
        Users purchaser = persistUser("purchaser");
        Users answerAuthor = persistUser("answer-author");
        Boards question = entityManager.persist(Boards.of(BoardCode.QUESTION, "질문"));
        Boards info = entityManager.persist(Boards.of(BoardCode.INFO, "정보"));

        Posts locked = persistPost(question, owner, "잠긴 질문", "needle 잠긴 본문", PostAccessType.POINT_REQUIRED);
        Posts waiting = persistPost(question, owner, "미채택 질문", "needle 공개 본문", PostAccessType.POINT_REQUIRED);
        Posts freeInfo = persistPost(info, owner, "정보글", "needle 정보 본문", PostAccessType.FREE);

        Comments answer = entityManager.persist(Comments.create(locked, answerAuthor.getUserId(), null, "채택 답변"));
        entityManager.persist(AcceptedComments.of(locked, answer, owner.getUserId()));
        entityManager.persist(PostAccess.of(purchaser, locked, 100));
        entityManager.flush();
        entityManager.clear();

        List<Long> nonBuyerResult = searchBody(nonBuyer.getUserId());
        List<Long> ownerResult = searchBody(owner.getUserId());
        List<Long> purchaserResult = searchBody(purchaser.getUserId());
        List<Long> answerAuthorResult = searchBody(answerAuthor.getUserId());

        assertThat(nonBuyerResult).containsExactlyInAnyOrder(waiting.getId(), freeInfo.getId());
        assertThat(ownerResult).containsExactlyInAnyOrder(locked.getId(), waiting.getId(), freeInfo.getId());
        assertThat(purchaserResult).containsExactlyInAnyOrder(locked.getId(), waiting.getId(), freeInfo.getId());
        assertThat(answerAuthorResult).containsExactlyInAnyOrder(locked.getId(), waiting.getId(), freeInfo.getId());
    }

    private List<Long> searchBody(Long viewerUserId) {
        return postsRepository.findFeedLatestWithFilter(
                        PostStatus.PUBLISHED,
                        null,
                        null,
                        "needle",
                        viewerUserId,
                        PostAccessType.POINT_REQUIRED,
                        BoardCode.QUESTION,
                        null,
                        PageRequest.of(0, 20)
                ).getContent().stream()
                .map(Posts::getId)
                .toList();
    }

    private Users persistUser(String suffix) {
        return entityManager.persist(Users.builder()
                .username("search-" + suffix)
                .passwordHash("hash")
                .name(suffix)
                .email(suffix + "@example.com")
                .build());
    }

    private Posts persistPost(Boards board, Users user, String title, String content, PostAccessType accessType) {
        Posts post = Posts.create(board, user, title, content, false);
        post.applyAccess(accessType);
        return entityManager.persist(post);
    }
}
