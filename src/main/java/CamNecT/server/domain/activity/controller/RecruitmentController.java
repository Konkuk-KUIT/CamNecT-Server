package CamNecT.server.domain.activity.controller;

import CamNecT.server.domain.activity.dto.request.RecruitmentApplyRequest;
import CamNecT.server.domain.activity.dto.request.RecruitmentRequest;
import CamNecT.server.domain.activity.dto.response.RecruitmentDetailResponse;
import CamNecT.server.domain.activity.model.recruitment.TeamRecruitment;
import CamNecT.server.domain.activity.service.RecruitmentService;
import CamNecT.server.global.common.auth.UserId;
import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.common.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Activity Recruitment", description = "대외활동 내 팀원 모집 및 지원 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/activity/recruitment")
@Validated
public class RecruitmentController {

    private final RecruitmentService recruitmentService;

    @Operation(summary = "팀원 모집글 생성", description = "특정 대외활동(대외활동/취업공고)에 대한 팀원 모집글을 작성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 activityId·제목·모집 기한·모집 인원·내용 검증 실패 / 46004 팀 모집을 지원하지 않는 활동 카테고리", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "46402 대상 활동을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 모집글 저장 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ApiResponse<TeamRecruitment> createRecruitment(
            @UserId Long userId,
            @RequestBody @Valid RecruitmentRequest request
    ) {
        return ApiResponse.success(recruitmentService.createRecruitment(userId, request));
    }

    @Operation(summary = "모집글 상세 조회", description = "특정 팀원 모집글의 상세 내용 및 팀 구성 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 모집글 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "46402 연결된 활동을 찾을 수 없음 / 46403 모집글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 작성자·프로필·북마크 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{recruitmentId}")
    public ApiResponse<RecruitmentDetailResponse> getRecruitmentDetail(
            @UserId Long userId,
            @PathVariable @Positive Long recruitmentId
    ) {
        return ApiResponse.success(recruitmentService.getRecruitmentDetail(userId, recruitmentId));
    }

    @Operation(summary = "팀원 모집글 수정", description = "팀원 모집글을 수정합니다. 작성자만 수정할 수 있습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 모집글 ID·요청값 검증 실패 / 46005 이미 마감된 모집글", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입 / 46101 모집글 작성자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "46403 모집글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 모집글 수정 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{recruitmentId}")
    public ApiResponse<String> updateRecruitment(
            @UserId Long userId,
            @PathVariable @Positive Long recruitmentId,
            @RequestBody @Valid RecruitmentRequest request
    ) {
        recruitmentService.updateRecruitment(userId, recruitmentId, request);
        return ApiResponse.success("수정이 완료되었습니다.");
    }

    @Operation(summary = "모집글 북마크 설정 (토글 방식)", description = "팀원 모집글에 대한 북마크 상태를 반전(Toggle)시킵니다. 호출 시마다 등록/해제 메시지를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 모집글 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "46403 모집글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 북마크 상태·개수 갱신 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{recruitmentId}/bookmark")
    public ApiResponse<String> toggleBookmark(
            @UserId Long userId,
            @PathVariable @Positive Long recruitmentId
    ) {
        boolean isBookmarked = recruitmentService.toggleRecruitmentBookmark(userId, recruitmentId);
        String message = isBookmarked ? "북마크가 등록되었습니다." : "북마크가 해제되었습니다.";
        return ApiResponse.success(message);
    }

    @Operation(summary = "팀 지원하기", description = "모집 중인 팀에 지원 신청을 보냅니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 모집글 ID·지원 내용 검증 실패 / 46001 본인 모집글에 지원 / 46002 이미 지원함 / 46003 마감된 모집글", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "41401 모집글 작성자 사용자 없음 / 46403 모집글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "48901 대기 중인 중복 팀 모집 요청 / 48902 이미 승인된 채팅방 관계", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 팀 지원·채팅 요청·알림 저장 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{recruitmentId}/apply")
    public ApiResponse<Long> applyToTeam(
            @UserId Long userId,
            @PathVariable @Positive Long recruitmentId,
            @RequestBody @Valid RecruitmentApplyRequest request
    ) {
        Long applicationId = recruitmentService.applyToTeam(userId, recruitmentId, request);
        return ApiResponse.success(applicationId);
    }

    /*
    * 팀 지원에 대한 승인은
    * ChatController에서 담당합니다!
    * (Coffechat요청과 recruitment 지원은 같은 형식에 type enum만 다름)
    */

    @Operation(summary = "팀원 모집 마감", description = "팀원 모집을 마감합니다. 작성자만 마감할 수 있습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 모집글 ID 형식 / 46005 이미 마감된 모집글", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입 / 46101 모집글 작성자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "46403 모집글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 모집 마감·대기 중인 팀 요청 일괄 거절 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{recruitmentId}/close")
    public ApiResponse<String> closeRecruitment(
            @PathVariable @Positive Long recruitmentId,
            @UserId Long userId
    ) {
        recruitmentService.closeRecruitment(userId, recruitmentId);
        return ApiResponse.success("팀원 모집이 성공적으로 마감되었습니다.");
    }

    @Operation(summary = "팀원 모집글 삭제", description = "팀원 모집글을 삭제합니다. 작성자 또는 관리자만 삭제할 수 있습니다. 삭제 후에도 관련 채팅방은 유지됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "모집글이 성공적으로 삭제되었습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 모집글 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입 / 46101 모집글 작성자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "46403 모집글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 모집글 삭제 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{recruitmentId}")
    public ApiResponse<Void> deleteRecruitment(
            @PathVariable @Positive Long recruitmentId,
            @UserId Long userId
    ) {
        recruitmentService.deleteRecruitment(userId, recruitmentId);
        return ApiResponse.success(null);
    }
}
