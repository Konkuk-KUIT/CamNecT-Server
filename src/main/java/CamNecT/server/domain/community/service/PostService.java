package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.dto.request.CreatePostRequest;
import CamNecT.server.domain.community.dto.request.UpdatePostRequest;
import CamNecT.server.domain.community.dto.response.*;

public interface PostService {
    CreatePostResponse create(Long userId, CreatePostRequest req);

    void update(Long userId, Long postId, UpdatePostRequest req);

    void delete(Long userId, Long postId);

    void deleteForModeration(Long adminId, Long postId);

    ToggleLikeResponse toggleLike(Long userId, Long postId);

    PostDetailResponse getDetail(Long userId, Long postId);

    void acceptComment(Long userId, Long postId, Long commentId);

    ToggleBookmarkResponse toggleBookmark(Long userId, Long postId);

    PurchasePostAccessResponse purchasePostAccess(Long userId, Long postId);
}
