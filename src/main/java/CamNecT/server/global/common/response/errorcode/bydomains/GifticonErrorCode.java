package CamNecT.server.global.common.response.errorcode.bydomains;

import CamNecT.server.global.common.response.errorcode.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GifticonErrorCode implements BaseErrorCode {

    // 470xx - 요청/검증
    PRODUCT_INACTIVE(HttpStatus.BAD_REQUEST, 47001, "판매 중인 상품이 아닙니다."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, 47002, "수량이 올바르지 않습니다."),
    INVALID_RECIPIENT_EMAIL(HttpStatus.BAD_REQUEST, 47003, "기프티콘을 받을 유효한 이메일이 필요합니다."),
    // 474xx - 리소스 없음
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, 47401, "상품을 찾을 수 없습니다."),

    // 479xx - 상태/규칙 위반
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, 47901, "이미 처리된 요청입니다.");

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;
}
