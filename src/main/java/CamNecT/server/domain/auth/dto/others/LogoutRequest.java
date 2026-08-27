package CamNecT.server.domain.auth.dto.others;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @Schema(description = "푸시 토큰 등록 시 사용한 현재 기기의 deviceId")
        @NotBlank
        @Size(max = 128)
        String deviceId
) {
}
