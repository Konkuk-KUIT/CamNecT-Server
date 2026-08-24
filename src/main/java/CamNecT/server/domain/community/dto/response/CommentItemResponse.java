package CamNecT.server.domain.community.dto.response;

import CamNecT.server.domain.community.dto.AuthorDto;

import java.time.LocalDateTime;

public record CommentItemResponse(
        Long commentId,
        Long userId,
        Long parentCommentId,
        String content,
        long likeCount,
        LocalDateTime createdAt,
        AuthorDto author
) {
}
