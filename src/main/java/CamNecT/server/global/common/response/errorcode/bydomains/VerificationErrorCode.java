package CamNecT.server.global.common.response.errorcode.bydomains;

import CamNecT.server.global.common.response.errorcode.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VerificationErrorCode implements BaseErrorCode {

    // 420xx - 요청/검증
    DOCUMENTS_REQUIRED(HttpStatus.BAD_REQUEST, 42020, "서류 파일은 최소 1개 필요합니다."),
    EMPTY_FILE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, 42022, "빈 파일은 업로드할 수 없습니다."),
    REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, 42011, "반려 사유가 필요합니다."),
    NO_ACTIVE_CODE(HttpStatus.BAD_REQUEST, 42030, "인증 코드가 존재하지 않습니다."),
    CODE_EXPIRED_OR_USED(HttpStatus.BAD_REQUEST, 42031, "인증 코드가 만료되었거나 이미 사용되었습니다."),
    INVALID_CODE(HttpStatus.BAD_REQUEST, 42032, "인증 코드가 올바르지 않습니다."),
    APPROVE_FIELDS_REQUIRED(HttpStatus.BAD_REQUEST, 42033, "승인에 필요한 학생 정보가 누락되었습니다."),

    // 421xx - 업로드 포맷/사이즈
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, 42123, "파일 크기가 제한을 초과했습니다."),
    UNSUPPORTED_CONTENT_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, 42124, "허용되지 않는 파일 형식입니다."),

    // 424xx - 리소스 없음
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, 42401, "요청을 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, 42402, "유저를 찾을 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, 42403, "파일을 찾을 수 없습니다."),

    //425xx - 외부 시스템
    MAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY, 42501, "메일 발송에 실패했습니다."),

    // 429xx - 상태/규칙 위반
    PENDING_ALREADY_EXISTS(HttpStatus.CONFLICT, 42930, "이미 처리 대기(PENDING) 중인 요청이 있습니다."),
    DOCUMENT_SUBMISSION_NOT_ALLOWED(HttpStatus.CONFLICT, 42931, "승인 대기 상태에서만 서류를 제출할 수 있습니다."),
    ONLY_PENDING_CAN_REVIEW(HttpStatus.CONFLICT, 42910, "PENDING 상태만 처리할 수 있습니다."),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, 42920, "인증 시도 횟수를 초과했습니다.");

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;
}
