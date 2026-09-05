package CamNecT.server.domain.gifticon.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfirmGifticonPurchaseRequest(
        @NotNull Long productId,
        @NotNull Integer quantity,
        @NotNull Integer spendPoints,
        @NotBlank @Size(max = 100) String clientRequestId,

        // 생략하면 구매자의 가입 이메일로 발송
        @Size(max = 100) String recipientName,
        @Schema(description = "수신자 이메일. 생략하거나 빈 문자열이면 구매자의 가입 이메일을 사용합니다.")
        @Email @Size(max = 255) String recipientEmail,
        @Size(max = 500) String giftMessage
) {
    public ConfirmGifticonPurchaseRequest {
        recipientEmail = recipientEmail == null || recipientEmail.isBlank() ? null : recipientEmail.trim();
    }

    // 알 수 없는 수신자 필드를 무시하면 의도하지 않은 주소로 주문될 수 있다.
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("지원하지 않는 구매 요청 필드입니다.");
    }
}
