package CamNecT.server.domain.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank
        @Size(max = CommunityRequestLimits.MAX_COMMENT_CONTENT_LENGTH)
        @Pattern(regexp = "^[\\P{Cc}\\r\\n\\t]*$", message = "댓글에 허용되지 않은 제어문자가 포함되어 있습니다.")
        String content,
        @Positive Long parentCommentId
) {}
