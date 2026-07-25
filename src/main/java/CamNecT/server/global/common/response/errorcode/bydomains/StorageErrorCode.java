package CamNecT.server.global.common.response.errorcode.bydomains;

import CamNecT.server.global.common.response.errorcode.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StorageErrorCode implements BaseErrorCode {

    // 490xx - 입력/검증
    STORAGE_EMPTY_FILE(HttpStatus.BAD_REQUEST, 49001, "업로드할 파일이 없습니다."),
    STORAGE_PREFIX_REQUIRED(HttpStatus.BAD_REQUEST, 49002, "저장 prefix가 필요합니다."),
    STORAGE_KEY_REQUIRED(HttpStatus.BAD_REQUEST, 49003, "storageKey가 필요합니다."),
    STORAGE_INVALID_PREFIX(HttpStatus.BAD_REQUEST, 49008, "storage prefix가 옳지 않습니다."),

    UNSUPPORTED_CONTENT_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, 49004, "허용되지 않은 Content-Type 입니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, 49005, "파일 용량이 제한을 초과했습니다."),
    UPLOAD_TICKET_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, 49006, "업로드 가능한 파일 개수 제한을 초과했습니다."),

    UPLOAD_TICKET_EXPIRED_OR_USED(HttpStatus.BAD_REQUEST, 49010, "업로드 티켓이 만료되었거나 이미 사용되었습니다."),
    UPLOAD_TICKET_MISMATCHED_OBJECT(HttpStatus.BAD_REQUEST, 49011, "업로드된 파일 정보가 티켓 정보와 일치하지 않습니다."),

    EMPTY_FILE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, 49020, "빈 파일은 업로드가 허가되지 않습니다."),
    INVALID_ATTACHMENT_METADATA(HttpStatus.BAD_REQUEST, 49021, "첨부파일 메타데이터가 올바르지 않습니다."),
    DUPLICATE_ATTACHMENT_KEY(HttpStatus.BAD_REQUEST, 49022, "중복된 첨부파일 키가 포함되어 있습니다."),
    // 491xx - 인증/토큰


    // 493xx - 권한/상태
    UPLOAD_TICKET_FORBIDDEN(HttpStatus.FORBIDDEN, 49310, "해당 파일을 사용할 권한이 없습니다."),

    // 494xx - 리소스
    STORAGE_NOT_FOUND(HttpStatus.NOT_FOUND, 49401, "파일을 찾을 수 없습니다."),
    UPLOAD_TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, 49410, "업로드 티켓을 찾을 수 없습니다."),

    // 499xx - 충돌
    STORAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 49901, "파일 업로드에 실패했습니다."),
    STORAGE_DOWNLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 49902, "파일 다운로드에 실패했습니다."),
    STORAGE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 49903, "파일 삭제에 실패했습니다."),
    STORAGE_MOVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 49904, "파일 이동에 실패했습니다.");


    private final HttpStatus httpStatus;
    private final int code;
    private final String message;
}
