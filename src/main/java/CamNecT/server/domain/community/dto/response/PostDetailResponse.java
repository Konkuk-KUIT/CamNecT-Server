package CamNecT.server.domain.community.dto.response;

import CamNecT.server.domain.community.dto.AuthorDto;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.ContentAccessStatus;

import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponse(
        Long postId,
        BoardCode boardCode,
        String title,
        String content,
        LocalDateTime createdAt,
        boolean anonymous,
        long viewCount,
        long bookmarkCount,
        long likeCount,
        boolean likedByMe,
        Long acceptedCommentId,
        List<Long> tagIds,
        List<PostAttachmentItemResponse> attachments,
        ContentAccessStatus accessStatus,
        boolean bookmarked,
        Integer requiredPoints,
        Integer myPoints,
        AuthorDto author
) {}
