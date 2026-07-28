package CamNecT.server.domain.report.controller;

import CamNecT.server.domain.report.dto.request.ReportCreateRequest;
import CamNecT.server.domain.report.dto.request.ReportProcessRequest;
import CamNecT.server.domain.report.dto.response.ReportCaseDetailResponse;
import CamNecT.server.domain.report.dto.response.ReportCaseSummaryResponse;
import CamNecT.server.domain.report.dto.response.ReportResultResponse;
import CamNecT.server.domain.report.model.ReportStatus;
import CamNecT.server.domain.report.model.TargetType;
import CamNecT.server.domain.report.service.ReportAttachmentService;
import CamNecT.server.domain.report.service.ReportService;
import CamNecT.server.global.common.auth.UserId;
import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.common.response.ErrorResponse;
import CamNecT.server.global.storage.dto.request.PresignUploadRequest;
import CamNecT.server.global.storage.dto.response.PresignUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Report", description = "신고 관리 관련 API")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportAttachmentService reportAttachmentService;

    /**
     * 일반 유저가 신고하는 메서드
     * POST /api/v1/reports
     * 요청: { reportedUserId, reportedPostId, postType, reportCategory, title, context, evidenceImageUrl }
     * 응답: { reportId, message, penaltyType }
     */
    @Operation(
            summary = "신고 제출",
            description = "신고 대상, 사유, 증거 이미지를 포함하여 신고를 제출합니다. 관리자 검토 후 자동으로 패널티가 적용됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "신고 성공적으로 제출됨",
                    content = @Content(schema = @Schema(implementation = ReportResultResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "40000 요청값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "40100 유효하지 않거나 만료된 JWT / 인증 헤더 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "44401 신고 대상 사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "50000 내부 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportResultResponse> createReport(
            @UserId Long reporterId,
            @RequestBody @Valid ReportCreateRequest request) {
        Long reportId = reportService.createReport(reporterId, request);
        ReportResultResponse response = ReportResultResponse.submitted(reportId);
        return ApiResponse.success(response);
    }

    /**
     * 증거 이미지 업로드용 Presigned URL 발급
     * POST /api/v1/reports/uploads/presign/evidence
     * 
     * 포트폴리오의 이미지 업로드 방식을 그대로 적용
     */
    @Operation(
            summary = "신고 증거 이미지 업로드 URL 발급",
            description = "신고에 첨부할 증거 이미지를 업로드하기 위한 Presigned URL을 발급합니다. 이미지 확장자만 허용됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Presigned URL 발급 성공",
                    content = @Content(schema = @Schema(implementation = PresignUploadResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "40000 요청값 검증 실패 / 49020 파일 크기가 0 이하",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "40100 유효하지 않거나 만료된 JWT / 인증 헤더 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "41302 정지된 사용자",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "413",
                    description = "49005 이미지 용량 제한 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "415",
                    description = "41500 지원하지 않는 Content-Type / 49004 허용되지 않은 이미지 파일 형식 (jpg, jpeg, png, webp만 허용)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "50000 Presigned URL 발급 또는 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/uploads/presign/evidence")
    public ApiResponse<PresignUploadResponse> presignEvidence(
            @UserId Long userId,
            @RequestBody @Valid PresignUploadRequest request) {
        PresignUploadResponse response = reportAttachmentService.presignEvidence(userId, request);
        return ApiResponse.success(response);
    }

    /**
     * 관리자가 신고 객체별 case 목록을 조회하는 메서드
     * GET /api/v1/reports/admin
     * 쿼리 파라미터: type (COMMUNITY, COMMUNITY_COMMENT, ACTIVITY, ACTIVITY_RECRUITMENT, USER, CHAT), status (RECEIVED, RESOLVED, REJECTED)
     */
    @Operation(
            summary = "신고 목록 조회 (관리자)",
            description = "동일 객체에 접수된 신고를 하나의 case로 묶어 조회합니다. 신고 타입과 상태로 필터링할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "신고 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = Page.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "40100 유효하지 않거나 만료된 JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "41301 관리자 권한이 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "50000 내부 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/admin")
    public ApiResponse<Page<ReportCaseSummaryResponse>> getReports(
            @UserId Long userId,
            @RequestParam(required = false) TargetType type,
            @RequestParam(required = false) ReportStatus status,
            Pageable pageable) {
        return ApiResponse.success(reportService.findAllReports(userId, type, status, pageable));
    }

    /**
     * 관리자가 신고 case 상세 정보를 조회하는 메서드
     * GET /api/v1/reports/admin/{caseId}
     */
    @Operation(
            summary = "신고 상세 조회 (관리자)",
            description = "대상 작성자, 개별 신고 제출 내역, 증거 이미지와 대상 사용자의 기존 제재 이력을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "신고 상세 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = ReportCaseDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "40100 유효하지 않거나 만료된 JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "41301 관리자 권한이 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "44401 신고를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "50000 내부 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/admin/{caseId}")
    public ApiResponse<ReportCaseDetailResponse> getReportDetail(
            @UserId Long userId,
            @PathVariable Long caseId) {
        return ApiResponse.success(reportService.getReportDetail(userId, caseId));
    }

    /**
     * 관리자가 신고를 처리하는 메서드 (승인/반려)
     * PATCH /api/v1/reports/admin/{caseId}/status
     * 승인 요청: { status: RESOLVED, decidedCategory, reason }
     * 반려 요청: { status: REJECTED, reason }
     */
    @Operation(
            summary = "신고 처리 (승인/반려)",
            description = "신고 case를 승인(RESOLVED) 또는 반려(REJECTED) 처리합니다. 승인 시 관리자가 확정한 decidedCategory가 필수이며 case당 패널티가 한 번만 적용됩니다.\n\n" +
                    "**패널티 체계:**\n" +
                    "- 1회: 경고 알림\n" +
                    "- 2회: 7일 정지\n" +
                    "- 3회: 영구 차단\n" +
                    "- 즉시 제재: 성희롱, 사기 → 1개 객체 승인 시 영구 차단"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "신고 처리 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "40000 요청값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "40100 유효하지 않거나 만료된 JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "41301 관리자 권한이 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "44401 신고를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "50000 내부 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PatchMapping("/admin/{caseId}/status")
    public ApiResponse<Void> processReport(
            @UserId Long userId,
            @PathVariable Long caseId,
            @RequestBody @Valid ReportProcessRequest request) {
        reportService.processReport(userId, caseId, request);
        return ApiResponse.success(null);
    }

    /**
     * 특정 유저의 신고 누적 수 조회 (관리자용)
     * GET /api/v1/reports/admin/users/{userId}/report-count
     */
    @Operation(
            summary = "사용자 신고 누적 수 조회 (관리자)",
            description = "특정 사용자에 대해 승인된 신고의 누적 수를 조회합니다. 패널티 결정에 사용됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "신고 누적 수 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "40100 유효하지 않거나 만료된 JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "41301 관리자 권한이 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "50000 내부 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/admin/users/{targetUserId}/report-count")
    public ApiResponse<Long> getReportCount(
            @UserId Long userId,
            @PathVariable Long targetUserId) {
        long reportCount = reportService.getResolvedReportCount(userId, targetUserId);
        return ApiResponse.success(reportCount);
    }
}
