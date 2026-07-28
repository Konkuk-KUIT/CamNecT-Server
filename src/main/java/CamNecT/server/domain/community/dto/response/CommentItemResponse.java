package CamNecT.server.domain.community.dto.response;

import CamNecT.server.domain.community.dto.AuthorDto;

public record CommentItemResponse(
        Long commentId,
        Long userId,
        Long parentCommentId,
        String content,
        long likeCount,
        AuthorDto author
) {
}
