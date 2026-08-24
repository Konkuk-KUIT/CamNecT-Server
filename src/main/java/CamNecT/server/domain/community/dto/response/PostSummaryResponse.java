package CamNecT.server.domain.community.dto.response;

import CamNecT.server.domain.community.dto.AuthorDto;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.ContentAccessStatus;
import CamNecT.server.domain.community.model.enums.PostAccessType;

import java.time.LocalDateTime;
import java.util.List;

public record PostSummaryResponse(
        Long postId,
        BoardCode boardCode,
        String title,
        String preview,
        LocalDateTime createdAt,
        long likeCount,
        long answerCount,     // 질문 탭 표시용(루트댓글 수)
        long commentCount,    // 전체댓글 수
        long bookmarkCount,
        boolean acceptedBadge,     // 질문글 채택완료 뱃지
        List<String> tags,
        AuthorDto author,
        String thumbnailUrl,   // 일단 null 가능
        PostAccessType accessType,
        ContentAccessStatus accessStatus,
        Integer requiredPoints,
        Integer myPoints
) {}
