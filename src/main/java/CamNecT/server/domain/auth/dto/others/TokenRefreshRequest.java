package CamNecT.server.domain.auth.dto.others;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRefreshRequest(
        @Schema(description = "로그인 또는 직전 재발급 응답에서 받은 Refresh Token")
        @NotBlank
        @Size(max = 4096)
        String refreshToken
) {}
