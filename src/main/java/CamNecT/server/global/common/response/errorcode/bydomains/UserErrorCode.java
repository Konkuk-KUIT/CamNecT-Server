package CamNecT.server.global.common.response.errorcode.bydomains;

import CamNecT.server.global.common.response.errorcode.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements BaseErrorCode {

    // 440xx - 입력/검증
    INVALID_TAG_IDS(HttpStatus.BAD_REQUEST, 44030, "유효하지 않은 태그가 포함되어 있습니다."),
    PORTFOLIO_THUMBNAIL_REQUIRED(HttpStatus.BAD_REQUEST, 44020, "썸네일 파일이 필요합니다."),
    SELF_FOLLOW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, 44040, "자기 자신을 팔로우할 수 없습니다."),

    // 441xx - 인증/토큰
    INSUFFICIENT_POINT(HttpStatus.BAD_REQUEST, 44101, "포인트 잔액이 부족합니다."),
    WALLET_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 44150, "포인트 지갑 생성에 실패했습니다."),

    // 443xx - 권한/상태
    USER_NOT_ADMIN(HttpStatus.FORBIDDEN, 44303, "관리자 유저가 아닙니다."),
    EXPERIENCE_FORBIDDEN(HttpStatus.FORBIDDEN, 44304, "해당 경력 정보에 대한 수정/삭제 권한이 없습니다."),
    CERTIFICATE_FORBIDDEN(HttpStatus.FORBIDDEN, 44305, "해당 자격증 정보에 대한 수정/삭제 권한이 없습니다."),
    EDUCATION_FORBIDDEN(HttpStatus.FORBIDDEN, 44306, "해당 학력 정보에 대한 수정/삭제 권한이 없습니다."),
    PORTFOLIO_FORBIDDEN(HttpStatus.FORBIDDEN, 44310, "포트폴리오에 대한 권한이 없습니다."),

    // 444xx - 리소스
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, 44401, "유저를 찾을 수 없습니다."),
    USER_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, 44402, "유저 프로필을 찾을 수 없습니다."),
    EXPERIENCE_NOT_FOUND(HttpStatus.NOT_FOUND, 44404, "해당 경력 정보를 찾을 수 없습니다."),
    CERTIFICATE_NOT_FOUND(HttpStatus.NOT_FOUND, 44405, "해당 자격증 정보를 찾을 수 없습니다."),
    EDUCATION_NOT_FOUND(HttpStatus.NOT_FOUND, 44406, "해당 학력 정보를 찾을 수 없습니다."),
    INSTITUTION_NOT_FOUND(HttpStatus.NOT_FOUND, 44407, "해당 학교 정보를 찾을 수 없습니다."),
    MAJOR_NOT_FOUND(HttpStatus.NOT_FOUND, 44408, "해당 전공 정보를 찾을 수 없습니다."),
    CAMPUS_NOT_FOUND(HttpStatus.NOT_FOUND, 44409, "해당 캠퍼스 정보를 찾을 수 없습니다."),
    PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, 44410, "해당 포트폴리오가 없습니다."),
    FOLLOW_NOT_FOUND(HttpStatus.NOT_FOUND, 44411, "팔로우 관계를 찾을 수 없습니다."),
  
    // 449xx - 충돌
    ALREADY_FOLLOWING(HttpStatus.CONFLICT, 44902, "이미 팔로우 중인 사용자입니다.");

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;
}
