package CamNecT.server.global.common.response.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode implements BaseErrorCode {
    // 503xx
    REDIS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, 50310,
            "일시적으로 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해주세요."),

    // 500xx
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 50000, "서버 내부 오류가 발생했습니다."),

    // 400xx
    BAD_REQUEST(HttpStatus.BAD_REQUEST, 40000, "잘못된 요청입니다."),

    // 401xx
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, 40100, "인증에 실패했습니다."),

    // 403xx
    FORBIDDEN(HttpStatus.FORBIDDEN, 40300, "권한이 없습니다."),

    // 404xx
    NOT_FOUND(HttpStatus.NOT_FOUND, 40400, "리소스를 찾을 수 없습니다."),

    // 405xx
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, 40500, "허용되지 않은 Http 메서드입니다."),

    // 406xx
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, 40600, "요청한 응답 형식을 제공할 수 없습니다."),

    // 410xx
    GONE(HttpStatus.GONE, 41000, "더 이상 제공하지 않는 API입니다."),

    // 413xx
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, 41300, "요청 본문 크기가 제한을 초과했습니다."),

    // 415xx
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, 41500, "지원하지 않는 Content-Type 입니다."),

    // 409xx
    CONFLICT(HttpStatus.CONFLICT, 40900, "충돌이 발생하였습니다. 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;
}
