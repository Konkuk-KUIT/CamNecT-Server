package CamNecT.server.domain.community.dto.response;

import java.util.List;

public record CommentListResponse(
        List<CommentItemResponse> items,
        Long nextCursorId,
        boolean hasNext
) {
}
