package CamNecT.server.domain.community.controller;

import CamNecT.server.domain.community.dto.request.CreateCommentRequest;
import CamNecT.server.domain.community.dto.request.UpdateCommentRequest;
import CamNecT.server.domain.community.dto.response.CreateCommentResponse;
import CamNecT.server.domain.community.dto.response.ToggleCommentLikeResponse;
import CamNecT.server.domain.community.service.CommentService;
import CamNecT.server.global.common.auth.UserId;
import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.common.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Community Comment", description = "커뮤니티 게시글 및 댓글 관리 관련 API")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/community")
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글 작성", description = "특정 게시글에 새로운 댓글 또는 대댓글을 작성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청값·게시글 ID 검증 실패 / 43030 부모 댓글이 다른 게시글에 속함", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43410 게시글 / 43412 부모 댓글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "43910 숨김 부모 댓글 / 43911 대댓글 깊이 제한 초과 / 43925 공개 상태가 아닌 게시글", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<CreateCommentResponse> create(
            @UserId Long userId,
            @PathVariable @Positive Long postId,
            @RequestBody @Valid CreateCommentRequest req
    ) {
        return ApiResponse.success(commentService.create(userId, postId, req));
    }

    // 댓글 목록 조회 (flat list: parentCommentId로 프론트에서 묶기)
    @Operation(
            summary = "댓글 목록 조회",
            description = "게시글의 전체 댓글을 평면 리스트(Flat List) 형태로 조회합니다. 대댓글은 parentCommentId를 기준으로 프론트엔드에서 그룹화 처리가 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 게시글 ID 또는 size 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43410 게시글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "43925 공개 상태가 아닌 게시글", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<List<CommentService.CommentRow>> list(
            @PathVariable @Positive Long postId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ApiResponse.success(commentService.list(postId, size));
    }

    @Operation(summary = "댓글 수정", description = "작성한 댓글의 내용을 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청 본문 또는 댓글 ID 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "43310 댓글 작성자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43411 댓글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "43912 공개 상태가 아닌 댓글 / 43925 공개 상태가 아닌 게시글 / 43928 채택된 댓글", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/comments/{commentId}")
    public ApiResponse<Void> update(
            @UserId Long userId,
            @PathVariable @Positive Long commentId,
            @RequestBody @Valid UpdateCommentRequest req
    ) {
        commentService.update(userId, commentId, req);
        return ApiResponse.success(null);
    }

    @Operation(summary = "댓글 삭제", description = "특정 댓글을 삭제합니다. 답글이 있는 경우 로직에 따라 상태가 변경될 수 있습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 댓글 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "43310 댓글 작성자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43411 댓글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "43912 공개 상태가 아닌 댓글 / 43925 공개 상태가 아닌 게시글 / 43928 채택된 댓글", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> delete(
            @UserId Long userId,
            @PathVariable @Positive Long commentId
    ) {
        commentService.delete(userId, commentId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "댓글 좋아요 설정 (토글 방식)", description = "댓글의 좋아요 상태를 반전(Toggle)시킵니다. 호출 시마다 좋아요 등록/해제 상태와 총 좋아요 수를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 댓글 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "43411 댓글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "43912 공개 상태가 아닌 댓글 / 43925 공개 상태가 아닌 게시글", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/comments/{commentId}/likes")
    public ApiResponse<ToggleCommentLikeResponse> toggleCommentLike(
            @UserId Long userId,
            @PathVariable @Positive Long commentId
    ) {
        return ApiResponse.success(commentService.toggleLike(userId, commentId));
    }


}
