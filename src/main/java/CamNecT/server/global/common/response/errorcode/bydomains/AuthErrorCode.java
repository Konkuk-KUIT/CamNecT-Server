package CamNecT.server.global.common.response.errorcode.bydomains;

import CamNecT.server.global.common.response.errorcode.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    // 410xx - 입력/검증
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, 41010, "비밀번호 형식이 올바르지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, 41011, "기존 비밀번호와 동일한 비밀번호는 사용할 수 없습니다."),
    TERMS_REQUIRED(HttpStatus.BAD_REQUEST, 41020, "필수 약관에 동의해야 합니다."),

    // 411xx - 인증/토큰
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, 41101, "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN_FORMAT(HttpStatus.UNAUTHORIZED, 41102, "Authorization 헤더 형식이 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, 41103, "유효하지 않은 토큰입니다."),
    ACCESS_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, 41104, "Access Token이 필요합니다."),
    TOKEN_TYPE_NOT_ALLOWED(HttpStatus.UNAUTHORIZED,41106,"토큰타입이 다릅니다."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED,41107,"이미 사용되었거나 폐기된 Refresh Token입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,41108,"만료된 Refresh Token입니다."),
    TOKEN_SHAPE_NOT_ALLOWED(HttpStatus.UNAUTHORIZED,41109,"토큰 양식이 올바르지 않습니다.(Bearer 누락등)"),

    // 413xx - 권한/상태
    USER_SUSPENDED(HttpStatus.FORBIDDEN, 41302, "정지된 사용자입니다."),
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, 41303, "탈퇴한 사용자입니다."),
    ACTIVE_ACCOUNT_REQUIRED(HttpStatus.FORBIDDEN, 41304, "관리자 승인 후 이용할 수 있습니다."),

    // 414xx - 리소스
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, 41401, "유저를 찾을 수 없습니다."),

    // 419xx - 충돌
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, 41901, "이미 가입된 이메일입니다."),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, 41902, "이미 사용 중인 아이디입니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, 41904, "서버가 생성하려는 자원이 이미 존재해서 충돌"),
    INITIAL_SETUP_NOT_ALLOWED(HttpStatus.CONFLICT, 41905, "초기 설정이 필요한 상태가 아닙니다.");
    private final HttpStatus httpStatus;
    private final int code;
    private final String message;
}
