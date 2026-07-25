package CamNecT.server.domain.community.dto.request;

import CamNecT.server.domain.community.model.enums.BoardCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.UniqueElements;

import java.util.List;

public record CreatePostRequest(
        @NotNull BoardCode boardCode,
        @NotBlank
        @Size(max = CommunityRequestLimits.MAX_TITLE_LENGTH)
        @Pattern(regexp = "^[^\\p{Cc}]*$", message = "제목에는 제어문자를 사용할 수 없습니다.")
        String title,
        @NotBlank
        @Size(max = CommunityRequestLimits.MAX_POST_CONTENT_LENGTH)
        @Pattern(regexp = "^[\\P{Cc}\\r\\n\\t]*$", message = "본문에 허용되지 않은 제어문자가 포함되어 있습니다.")
        String content,
        Boolean anonymous,
        @Size(max = CommunityRequestLimits.MAX_TAGS_PER_POST)
        @UniqueElements
        List<@NotNull @Positive Long> tagIds,
        @Size(max = CommunityRequestLimits.MAX_ATTACHMENTS_PER_POST)
        List<@NotNull @Valid AttachmentRequest> attachments
){}
