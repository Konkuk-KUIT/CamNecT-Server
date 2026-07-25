package CamNecT.server.domain.community.controller;

import CamNecT.server.domain.community.dto.request.CreatePostRequest;
import CamNecT.server.domain.community.dto.request.UpdatePostRequest;
import CamNecT.server.domain.community.dto.response.*;
import CamNecT.server.domain.community.service.PostAttachmentDownloadService;
import CamNecT.server.domain.community.service.PostAttachmentsService;
import CamNecT.server.domain.community.service.PostQueryService;
import CamNecT.server.domain.community.service.PostQueryService.*;
import CamNecT.server.domain.community.service.PostService;
import CamNecT.server.global.common.auth.UserId;
import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.common.response.ErrorResponse;
import CamNecT.server.global.storage.dto.request.PresignUploadBatchRequest;
import CamNecT.server.global.storage.dto.response.PresignDownloadResponse;
import CamNecT.server.global.storage.dto.response.PresignUploadBatchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static CamNecT.server.domain.community.dto.request.CommunityRequestLimits.MAX_SEARCH_KEYWORD_LENGTH;

@Tag(name = "Community Post", description = "커뮤니티 게시글 관련 API (CRUD, 좋아요, 북마크, 채택, 구매)")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/community/posts")
public class PostController {

    private final PostService postService;
    private final PostQueryService postQueryService;
    private final PostAttachmentDownloadService postAttachmentDownloadService;
    private final PostAttachmentsService postAttachmentsService;

    @Operation(summary = "게시글 작성", description = "새로운 게시글을 등록합니다. 첨부파일이 있는 경우 Presigned URL 발급 API를 먼저 호출해야 합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청값 검증 실패 / 43060 유효하지 않은 태그 / 49010 만료·사용된 업로드 티켓 / 49011 업로드 파일 불일치 / 49021 첨부 메타데이터 오류 / 49022 첨부 키 중복", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "49310 업로드 티켓 사용 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43420 게시판 / 49410 업로드 티켓 / 49401 업로드 파일을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "43926 비활성 태그", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "49006 첨부파일 개수 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "49902 업로드 파일 확인 실패 / 49904 파일 이동 실패 / 50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ApiResponse<CreatePostResponse> create(
            @UserId Long userId,
            @RequestBody @Valid CreatePostRequest req
    ) {
        return ApiResponse.success(postService.create(userId, req));
    }

    //리스트: /api/community/posts
    @Operation(
            summary = "게시글 목록 조회 (No-Offset 페이징)",
            description = "탭(Tab), 정렬(Sort), 태그, 키워드 검색을 지원하는 게시글 목록 조회 API입니다. 커서 기반 페이징(cursorId, cursorValue)을 사용합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 탭·정렬·쿼리 형식 / 43040 유효하지 않은 커서 / 43041 유효하지 않은 검색어", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "43510 게시글 통계 누락 / 50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ApiResponse<PostListResponse> list(
            @UserId Long userId,
            @RequestParam(defaultValue = "ALL") Tab tab,
            @RequestParam(defaultValue = "RECOMMENDED") Sort sort,
            @RequestParam(required = false) @Positive Long tagId,
            @RequestParam(required = false)
            @Size(max = MAX_SEARCH_KEYWORD_LENGTH)
            @Pattern(regexp = "^[^\\p{Cc}]*$", message = "검색어에 제어문자를 사용할 수 없습니다.")
            String keyword,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(required = false) Long cursorValue,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ApiResponse.success(postQueryService.getPosts(userId, tab, sort, tagId, keyword, cursorId, cursorValue, size));
    }

    @Operation(summary = "게시글 수정", description = "본인이 작성한 게시글의 내용을 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청값·게시글 ID 검증 실패 / 43060 유효하지 않은 태그 / 43061 변경할 필드 없음 / 49010 만료·사용된 업로드 티켓 / 49011 업로드 파일 불일치 / 49021 첨부 메타데이터 오류 / 49022 첨부 키 중복", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "43320 게시글 수정 권한 없음 / 49310 업로드 티켓 사용 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43410 게시글 / 49410 업로드 티켓 / 49401 업로드 파일을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "43926 비활성 태그", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "49006 첨부파일 개수 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "49902 업로드 파일 확인 실패 / 49904 파일 이동 실패 / 50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{postId}")
    public ApiResponse<Void> update(
            @UserId Long userId,
            @PathVariable @Positive Long postId,
            @RequestBody @Valid UpdatePostRequest req
    ) {
        postService.update(userId, postId, req);
        return ApiResponse.success(null);
    }

    @Operation(summary = "게시글 삭제", description = "특정 게시글을 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 게시글 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "43320 작성자 또는 관리자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43410 게시글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "43920 채택된 질문은 삭제할 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> delete(
            @UserId Long userId,
            @PathVariable @Positive Long postId
    ) {
        postService.delete(userId, postId);
        return ApiResponse.success(null);
    }


    @Operation(summary = "게시글 상세 조회", description = "게시글의 상세 내용과 첨부파일 목록, 좋아요/북마크 여부 등을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 게시글 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43410 게시글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "43925 공개 상태가 아닌 게시글", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{postId}")
    public ApiResponse<PostDetailResponse> getDetail(
            @UserId Long userId,
            @PathVariable @Positive Long postId
    ) {
        return ApiResponse.success(postService.getDetail(userId, postId));
    }

    //좋아요
    @Operation(summary = "게시글 좋아요 (토글 방식)", description = "게시글의 좋아요 상태를 반전(Toggle)시킵니다. 현재 상태와 총 좋아요 수를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 게시글 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43410 게시글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "43927 본인 게시글 좋아요 불가 / 40900 포인트 적립 동시성 충돌", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "44150 포인트 지갑 생성 실패 / 50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{postId}/likes")
    public ApiResponse<ToggleLikeResponse> toggleLike(
            @UserId Long userId,
            @PathVariable @Positive Long postId
    ) {
        return ApiResponse.success(postService.toggleLike(userId, postId));
    }

    //댓글 채택
    @Operation(summary = "댓글 채택", description = "질문글(Q&A) 등에서 특정 댓글을 해결책으로 채택합니다. 작성자만 가능합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 게시글 또는 댓글 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "43320 게시글 작성자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43410 게시글 / 43411 댓글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "43921 질문 게시판 아님 / 43922 다른 게시글의 댓글 / 43923 채택 불가 댓글 / 43924 이미 채택됨 / 43925 공개 상태가 아닌 게시글 / 43929 자기 댓글 채택 / 40900 포인트 적립 충돌", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "44150 포인트 지갑 생성 실패 / 50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{postId}/comments/{commentId}/accept")
    public ApiResponse<Void> accept(
            @UserId Long userId,
            @PathVariable @Positive Long postId,
            @PathVariable @Positive Long commentId
    ) {
        postService.acceptComment(userId, postId, commentId);
        return ApiResponse.success(null);
    }

    //북마크
    @Operation(summary = "게시글 북마크 (토글 방식)", description = "게시글을 북마크에 추가하거나 제거(Toggle)합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 게시글 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43410 게시글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{postId}/bookmarks")
    public ApiResponse<ToggleBookmarkResponse> toggleBookmark(
            @UserId Long userId,
            @PathVariable @Positive Long postId
    ) {
        return ApiResponse.success(postService.toggleBookmark(userId, postId));
    }

    //글 구매
    @Operation(summary = "유료 글 액세스 권한 구매", description = "포인트를 지불하여 유료 게시글의 열람 권한을 획득합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 게시글 ID 형식 / 44101 포인트 부족", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43410 게시글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "43925 공개 상태가 아닌 게시글 / 40900 포인트 잔액 동시성 충돌", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "44150 포인트 지갑 생성 실패 / 50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{postId}/access/purchase")
    public ApiResponse<PurchasePostAccessResponse> purchaseAccess(
            @UserId Long userId,
            @PathVariable @Positive Long postId
    ) {
        return ApiResponse.success(postService.purchasePostAccess(userId, postId));
    }

    @Operation(
            summary = "첨부파일 업로드용 Presigned URL 발급",
            description = """
                게시글에 포함될 파일을 S3에 업로드하기 위한 Presigned URL을 발급합니다.
                - 업로드는 temp 경로로 수행됩니다.
                - 게시글 저장/수정 시 consume되어 최종 경로로 이동됩니다.
                - 규칙: items[0]은 썸네일(이미지 jpg/png/webp)가 권장됩니다.
                - 규칙: items[1..]은 일반 첨부(예: pdf 포함 허용)입니다.
                - 규칙: items[0]에 pdf가 들어오면 썸네일은 null입니다.
            """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 요청 본문 / 49020 비어 있거나 크기가 0 이하인 파일 / 49021 첨부 메타데이터 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "49005 첨부파일 용량 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type / 49004 허용되지 않은 첨부파일 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "49006 첨부파일 개수 또는 미사용 티켓 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 Presigned URL 발급 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/uploads/presign")
    public ApiResponse<PresignUploadBatchResponse> presignAttachmentUpload(
            @UserId Long userId,
            @RequestBody @Valid PresignUploadBatchRequest req
    ) {
        return ApiResponse.success(postAttachmentsService.presignAttachmentsBatch(userId, req));
    }

    @Operation(summary = "첨부파일 다운로드 URL 발급(보험)", description = "게시글의 첨부파일을 다운로드하기 위한 임시 URL을 발급받습니다.(getDetail()에서 첨부파일 발급 실패시 호출)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 게시글 또는 첨부파일 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "43320 유료 게시글 접근권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43410 게시글 / 43430 첨부 메타데이터 / 49401 저장 파일을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "43925 공개 상태가 아닌 게시글", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "49902 저장 파일 확인 실패 / 50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{postId}/attachments/{attachmentId}/download-url")
    public ApiResponse<PresignDownloadResponse> downloadUrl(
            @UserId Long userId,
            @PathVariable @Positive Long postId,
            @PathVariable @Positive Long attachmentId
    ) {
        return ApiResponse.success(
                postAttachmentDownloadService.presignDownload(userId, postId, attachmentId)
        );
    }
}
