package CamNecT.server.global.storage.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PresignUploadBatchRequest(List<@NotNull @Valid Item> items) {
    public record Item(
            @NotBlank @Size(max = 100) String contentType,
            @Positive long size,
            @NotBlank
            @Size(max = 255)
            @Pattern(regexp = "^[^\\\\/\\p{Cc}]+$", message = "파일명에 경로 구분자나 제어문자를 사용할 수 없습니다.")
            String originalFilename
    ) {}
}
