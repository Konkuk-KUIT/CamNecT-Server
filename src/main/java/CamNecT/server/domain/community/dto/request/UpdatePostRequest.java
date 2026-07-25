package CamNecT.server.domain.community.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.UniqueElements;

import java.util.List;

public record UpdatePostRequest(
        @Size(max = CommunityRequestLimits.MAX_TITLE_LENGTH)
        @Pattern(regexp = "^(?=.*\\S)[^\\p{Cc}]*$", message = "제목은 공백일 수 없고 제어문자를 포함할 수 없습니다.")
        String title,
        @Size(max = CommunityRequestLimits.MAX_POST_CONTENT_LENGTH)
        @Pattern(regexp = "(?s)^(?=.*\\S)[\\P{Cc}\\r\\n\\t]*$", message = "본문은 공백일 수 없고 허용되지 않은 제어문자를 포함할 수 없습니다.")
        String content,
        Boolean anonymous,
        @Size(max = CommunityRequestLimits.MAX_TAGS_PER_POST)
        @UniqueElements
        List<@NotNull @Positive Long> tagIds,
        @Size(max = CommunityRequestLimits.MAX_ATTACHMENTS_PER_POST)
        List<@NotNull @Valid AttachmentRequest> attachments
) {
    @JsonIgnore
    public boolean isAnyFieldPresent() {
        return title != null
                || content != null
                || anonymous != null
                || tagIds != null
                || attachments != null;
    }
}
