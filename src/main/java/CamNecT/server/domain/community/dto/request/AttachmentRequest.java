package CamNecT.server.domain.community.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AttachmentRequest(
        @NotBlank
        @Size(max = CommunityRequestLimits.MAX_FILE_KEY_LENGTH)
        @Pattern(regexp = "^[^\\p{Cc}]+$", message = "파일 키에 제어문자를 사용할 수 없습니다.")
        String fileKey,
        @Positive Integer width,
        @Positive Integer height,
        @Positive Long fileSize
) {
    @AssertTrue(message = "이미지 너비와 높이는 함께 전달해야 합니다.")
    @JsonIgnore
    public boolean isDimensionPairValid() {
        return (width == null) == (height == null);
    }
}
