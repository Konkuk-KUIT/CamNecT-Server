package CamNecT.server.domain.community.repository.Comments;

import CamNecT.server.domain.community.model.Boards;
import CamNecT.server.domain.community.model.Comments.Comments;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class CommentsRepositoryDeleteTest {

    @Autowired TestEntityManager entityManager;
    @Autowired CommentsRepository commentsRepository;

    @Test
    void repliesAreDeletedBeforePublishedAndSoftDeletedRoots() {
        Users author = entityManager.persist(Users.builder()
                .username("comment-delete-author")
                .passwordHash("hash")
                .name("작성자")
                .email("comment-delete@example.com")
                .build());
        Boards board = entityManager.persist(Boards.of(BoardCode.INFO, "정보"));
        Posts post = entityManager.persist(Posts.create(board, author, "제목", "본문", false));

        Comments publishedRoot = entityManager.persist(
                Comments.create(post, author.getUserId(), null, "공개 루트 댓글")
        );
        Comments deletedRoot = Comments.create(post, author.getUserId(), null, "삭제된 루트 댓글");
        deletedRoot.deleteSoft();
        entityManager.persist(deletedRoot);

        entityManager.persist(Comments.create(post, author.getUserId(), publishedRoot, "공개 루트의 대댓글"));
        entityManager.persist(Comments.create(post, author.getUserId(), deletedRoot, "삭제된 루트의 대댓글"));
        entityManager.flush();
        entityManager.clear();

        commentsRepository.deleteRepliesByPostId(post.getId());
        commentsRepository.deleteRootsByPostId(post.getId());
        entityManager.flush();

        assertThat(commentsRepository.findAll()).isEmpty();
    }
}
