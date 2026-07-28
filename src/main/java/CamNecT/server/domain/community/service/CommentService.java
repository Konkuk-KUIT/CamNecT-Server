package CamNecT.server.domain.community.service;


import CamNecT.server.domain.community.dto.request.CreateCommentRequest;
import CamNecT.server.domain.community.dto.request.UpdateCommentRequest;
import CamNecT.server.domain.community.dto.response.CommentListResponse;
import CamNecT.server.domain.community.dto.response.CreateCommentResponse;
import CamNecT.server.domain.community.dto.response.ToggleCommentLikeResponse;

public interface CommentService {

    CreateCommentResponse create(Long userId, Long postId, CreateCommentRequest req);

    void update(Long userId, Long commentId, UpdateCommentRequest req);

    void delete(Long userId, Long commentId);

    ToggleCommentLikeResponse toggleLike(Long userId, Long commentId);

    CommentListResponse list(Long postId, Long cursorId, int size);
}
