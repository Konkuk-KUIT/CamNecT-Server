package CamNecT.server.domain.portfolio.controller;

import CamNecT.server.domain.portfolio.dto.request.PortfolioRequest;
import CamNecT.server.domain.portfolio.dto.response.PortfolioDetailResponse;
import CamNecT.server.domain.portfolio.dto.response.PortfolioPreviewResponse;
import CamNecT.server.domain.portfolio.dto.response.PortfolioResponse;
import CamNecT.server.domain.portfolio.service.PortfolioAttachmentService;
import CamNecT.server.domain.portfolio.service.PortfolioService;
import CamNecT.server.global.common.auth.UserId;
import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.common.response.ErrorResponse;
import CamNecT.server.global.storage.dto.request.PresignUploadBatchRequest;
import CamNecT.server.global.storage.dto.request.PresignUploadRequest;
import CamNecT.server.global.storage.dto.response.PresignUploadBatchResponse;
import CamNecT.server.global.storage.dto.response.PresignUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Portfolio", description = "포트폴리오 관리 관련 API")
@RestController
@RequestMapping("/api/portfolio/{portfolioUserId}")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioAttachmentService portfolioAttachmentService;

    @Operation(summary = "포트폴리오 목록 조회", description = "본인은 전체 목록, 다른 사용자는 공개 포트폴리오 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 portfolioUserId 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44401 대상 사용자를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 포트폴리오 목록 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ApiResponse<PortfolioResponse<List<PortfolioPreviewResponse>>> portfolioPreview(
            @UserId Long userId,
            @PathVariable Long portfolioUserId
    ) {
        return ApiResponse.success(portfolioService.portfolioPreview(userId, portfolioUserId));
    }

    @Operation(summary = "포트폴리오 상세 조회", description = "소유자는 전체, 다른 사용자는 공개 상태의 포트폴리오만 조회할 수 있습니다. 첨부 URL 발급 실패는 해당 URL을 null로 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 portfolioUserId 또는 portfolioId 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자 / 44310 비공개 포트폴리오 조회 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44410 포트폴리오가 없거나 URL의 사용자와 소유자가 일치하지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 상세·작성자·첨부 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{portfolioId}")
    public ApiResponse<PortfolioResponse<PortfolioDetailResponse>> portfolioDetail(
            @UserId Long userId,
            @PathVariable Long portfolioUserId,
            @PathVariable Long portfolioId
    ) {
        return ApiResponse.success(portfolioService.portfolioDetail(userId, portfolioUserId, portfolioId));
    }

    @Operation(summary = "포트폴리오 생성", description = "Presigned URL로 업로드한 썸네일·첨부 티켓을 소비하여 포트폴리오를 생성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청값·날짜 검증 실패 / 44020 썸네일 키 누락 / 49010 만료·사용된 티켓 / 49011 업로드 객체와 티켓 불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자 / 44310 URL 사용자와 로그인 사용자 불일치 / 49310 티켓 소유자·목적 불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "49410 업로드 티켓 / 49401 업로드 파일을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type / 49004 썸네일 파일 형식 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "49006 첨부파일 개수 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "49902 업로드 파일 확인 실패 / 49904 파일 이동 실패 / 50000 저장 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ApiResponse<PortfolioPreviewResponse> createPortfolio(
            @UserId Long userId,
            @PathVariable Long portfolioUserId,
            @RequestBody @Valid PortfolioRequest portfolioRequest
    ) {
        return ApiResponse.success(portfolioService.create(userId, portfolioUserId, portfolioRequest));
    }

    @Operation(summary = "포트폴리오 수정", description = "소유자의 포트폴리오 정보와 썸네일·첨부 목록을 교체합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 ID·요청값·날짜 검증 실패 / 49010 만료·사용된 티켓 / 49011 업로드 객체와 티켓 불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자 / 44310 소유자가 아님 / 49310 티켓 소유자·목적 불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44410 포트폴리오 / 49410 업로드 티켓 / 49401 업로드 파일을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type / 49004 썸네일 파일 형식 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "49006 첨부파일 개수 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "49902 업로드 파일 확인 실패 / 49904 파일 이동 실패 / 50000 수정 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{portfolioId}")
    public ApiResponse<PortfolioPreviewResponse> updatePortfolio(
            @UserId Long userId,
            @PathVariable Long portfolioId,
            @PathVariable Long portfolioUserId,
            @RequestBody @Valid PortfolioRequest portfolioRequest
    ) {
        return ApiResponse.success(portfolioService.update(userId, portfolioUserId, portfolioId, portfolioRequest));
    }

    @Operation(summary = "포트폴리오 삭제", description = "소유자 또는 관리자가 포트폴리오와 파일 연결을 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 portfolioUserId 또는 portfolioId 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자 / 44310 소유자 또는 관리자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44410 포트폴리오가 없거나 URL의 사용자와 소유자가 일치하지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 삭제 또는 내부 오류 (파일 삭제 실패는 커밋 후 로그 처리)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{portfolioId}")
    public ApiResponse<String> deletePortfolio(
            @UserId Long userId,
            @PathVariable Long portfolioUserId,
            @PathVariable Long portfolioId
    ) {
        portfolioService.delete(userId, portfolioUserId, portfolioId);
        return ApiResponse.success("포트폴리오가 삭제되었습니다.");
    }

    @Operation(summary = "공개 여부 설정", description = "소유자의 포트폴리오 공개 상태를 토글하고 최종 상태를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 portfolioUserId 또는 portfolioId 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자 / 44310 소유자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44410 포트폴리오가 없거나 URL의 사용자와 소유자가 일치하지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 공개 상태 변경 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{portfolioId}/public")
    public ApiResponse<Boolean> togglePublic(
            @UserId Long userId,
            @PathVariable Long portfolioUserId,
            @PathVariable Long portfolioId
    ) {
        return ApiResponse.success(portfolioService.togglePublic(userId, portfolioUserId, portfolioId));
    }

    @Operation(summary = "즐겨찾기 설정", description = "소유자의 포트폴리오 즐겨찾기 상태를 토글하고 최종 상태를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 portfolioUserId 또는 portfolioId 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자 / 44310 소유자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44410 포트폴리오가 없거나 URL의 사용자와 소유자가 일치하지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 즐겨찾기 상태 변경 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{portfolioId}/favorite")
    public ApiResponse<Boolean> toggleFavorite(
            @UserId Long userId,
            @PathVariable Long portfolioUserId,
            @PathVariable Long portfolioId
    ) {
        return ApiResponse.success(portfolioService.toggleFavorite(userId, portfolioUserId, portfolioId));
    }

    @Operation(summary = "썸네일 업로드용 Presigned URL 발급", description = "소유자가 이미지 썸네일을 업로드할 수 있는 단건 Presigned URL을 발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청값 검증 실패 / 49020 파일 크기가 0 이하", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자 / 44310 URL 사용자와 로그인 사용자 불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "49005 썸네일 용량 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type / 49004 허용되지 않은 이미지 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "49006 미사용 썸네일 티켓 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 Presigned URL 발급 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/uploads/presign/thumbnail")
    public ApiResponse<PresignUploadResponse> presignThumbnail(
            @UserId Long userId,
            @PathVariable Long portfolioUserId,
            @RequestBody @Valid PresignUploadRequest req
    ) {
        return ApiResponse.success(portfolioAttachmentService.presignThumbnail(userId, portfolioUserId, req));
    }

    @Operation(summary = "첨부파일 업로드용 Presigned URL 발급", description = "소유자가 최대 설정 개수의 첨부파일을 업로드할 수 있는 Presigned URL을 다건 발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청값·파일명 검증 실패 / 49020 파일 목록이 비었거나 파일 크기가 0 이하", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자 / 44310 URL 사용자와 로그인 사용자 불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "49005 첨부파일 용량 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type / 49004 허용되지 않은 첨부파일 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "49006 첨부파일 개수 또는 미사용 티켓 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 Presigned URL 발급 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/uploads/presign/assets")
    public ApiResponse<PresignUploadBatchResponse> presignAssetsBatch(
            @UserId Long userId,
            @PathVariable Long portfolioUserId,
            @RequestBody @Valid PresignUploadBatchRequest req
    ) {
        return ApiResponse.success(portfolioAttachmentService.presignAssetsBatch(userId, portfolioUserId, req));
    }
}
