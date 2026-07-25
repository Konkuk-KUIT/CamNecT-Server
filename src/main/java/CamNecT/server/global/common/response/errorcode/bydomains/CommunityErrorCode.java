package CamNecT.server.global.common.response.errorcode.bydomains;

import CamNecT.server.global.common.response.errorcode.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommunityErrorCode implements BaseErrorCode {

    // 430xx - 요청/검증
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, 43040, "잘못된 커서 요청입니다."),
    INVALID_SEARCH_KEYWORD(HttpStatus.BAD_REQUEST, 43041, "검색어 형식이 올바르지 않습니다."),
    INVALID_TAG_IDS(HttpStatus.BAD_REQUEST, 43060, "유효하지 않은 태그가 포함되어 있습니다."),
    EMPTY_POST_UPDATE(HttpStatus.BAD_REQUEST, 43061, "수정할 게시글 필드를 하나 이상 전달해야 합니다."),
    PARENT_COMMENT_NOT_IN_POST(HttpStatus.BAD_REQUEST, 43030, "부모 댓글이 해당 게시글에 속하지 않습니다."),

    // 433xx - 권한
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, 43310, "해당 댓글에 대한 권한이 없습니다."),
    POST_FORBIDDEN(HttpStatus.FORBIDDEN, 43320, "해당 게시글에 대한 권한이 없습니다."),

    // 434xx - 리소스 없음
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, 43410, "게시글을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, 43411, "댓글을 찾을 수 없습니다."),
    PARENT_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, 43412, "부모 댓글을 찾을 수 없습니다."),
    BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, 43420, "게시판을 찾을 수 없습니다."),
    POST_STATS_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, 43510, "게시글 통계 데이터가 누락되었습니다."),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, 43430, "첨부파일을 찾을 수 없습니다."),

    // 439xx - 상태/규칙 위반
    CANNOT_REPLY_TO_HIDDEN_COMMENT(HttpStatus.CONFLICT, 43910, "숨김 처리된 댓글에는 답글을 달 수 없습니다."),
    COMMENT_MAX_DEPTH_EXCEEDED(HttpStatus.CONFLICT, 43911, "대댓글은 2단계까지만 허용됩니다."),
    COMMENT_NOT_PUBLISHED(HttpStatus.CONFLICT, 43912, "공개 상태의 댓글만 변경할 수 있습니다."),
    CANNOT_DELETE_ACCEPTED_QUESTION(HttpStatus.CONFLICT, 43920, "채택된 질문은 삭제할 수 없습니다."),
    ONLY_QUESTION_CAN_ACCEPT(HttpStatus.CONFLICT, 43921, "질문 게시판에서만 채택할 수 있습니다."),
    COMMENT_NOT_IN_POST(HttpStatus.CONFLICT, 43922, "댓글이 해당 게시글에 속하지 않습니다."),
    CANNOT_ACCEPT_UNPUBLISHED_COMMENT(HttpStatus.CONFLICT, 43923, "삭제/숨김 댓글은 채택할 수 없습니다."),
    ALREADY_ACCEPTED(HttpStatus.CONFLICT, 43924, "이미 채택된 질문입니다."),
    POST_NOT_PUBLISHED(HttpStatus.CONFLICT, 43925, "게시글이 공개 상태가 아닙니다."),
    INACTIVE_TAG(HttpStatus.CONFLICT, 43926, "비활성화된 태그입니다."),
    CANNOT_LIKE_OWN_POST(HttpStatus.CONFLICT, 43927, "본인의 글에 좋아요를 누를 수 없습니다."),
    CANNOT_MODIFY_ACCEPTED_COMMENT(HttpStatus.CONFLICT, 43928, "채택된 댓글은 수정하거나 삭제할 수 없습니다."),
    CANNOT_ACCEPT_OWN_COMMENT(HttpStatus.CONFLICT, 43929, "질문 작성자는 본인의 댓글을 채택할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;
}
