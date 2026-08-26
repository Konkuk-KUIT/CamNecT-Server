package CamNecT.server.domain.activity.dto.request;

import CamNecT.server.domain.activity.model.enums.ActivityCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ActivityRequest(
        @NotNull(message = "카테고리는 필수입니다.")
        ActivityCategory category,

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,

        List<@Positive(message = "태그 ID는 양수여야 합니다.") Long> tagIds,
        String content,
        String thumbnailKey,
        List<String> attachmentKey
) {
}
