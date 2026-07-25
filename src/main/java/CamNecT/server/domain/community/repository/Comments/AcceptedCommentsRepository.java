package CamNecT.server.domain.community.repository.Comments;

import CamNecT.server.domain.community.model.Comments.AcceptedComments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AcceptedCommentsRepository extends JpaRepository<AcceptedComments, Long> {

    boolean existsByPost_Id(Long postId);

    boolean existsByComment_Id(Long commentId);

    Optional<AcceptedComments> findByPost_Id(Long postId);

    @Query("select ac.post.id from AcceptedComments ac where ac.post.id in :postIds")
    List<Long> findAcceptedPostIds(@Param("postIds") Collection<Long> postIds);

    void deleteByPost_Id(Long postId);
}
