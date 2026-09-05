package CamNecT.server.domain.gifticon.controller;

import CamNecT.server.domain.gifticon.dto.request.ConfirmGifticonPurchaseRequest;
import CamNecT.server.domain.gifticon.dto.response.*;
import CamNecT.server.domain.gifticon.service.GifticonPurchaseService;
import CamNecT.server.domain.gifticon.service.GifticonService;
import CamNecT.server.domain.gifticon.service.GifticonService.Sort;
import CamNecT.server.global.common.auth.UserId;
import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.common.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/gifticons")
public class GifticonController {

    private final GifticonService gifticonService;
    private final GifticonPurchaseService purchaseService;

    @Operation(summary = "기프티콘 샵 홈(상품목록+내포인트)", description = "서버 캐시 DB의 상품목록, 사용자 포인트, 기본 수신 이메일을 한번에 내려줍니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 지원하지 않는 정렬값", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 상품 목록·포인트 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/home")
    public ApiResponse<GifticonHomeResponse> home(
            @UserId Long userId,
            @RequestParam(defaultValue = "POPULAR") Sort sort
    ) {
        return ApiResponse.success(gifticonService.home(userId, sort));
    }

    @Operation(summary = "상품 상세", description = "상품 상세 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 상품 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "47401 상품을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/products/{productId}")
    public ApiResponse<GifticonProductDetailResponse> detail(
            @PathVariable Long productId
    ) {
        return ApiResponse.success(gifticonService.productDetail(productId));
    }

    @Operation(summary = "구매 확정", description = "포인트 차감과 구매요청 저장 후 관리자가 엑셀의 수신자 이메일로 구매·발송합니다. recipientEmail을 생략하면 가입 이메일을 사용합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청값 검증 실패 / 47001 판매 중이 아닌 상품 / 47002 수량 오류 / 47003 수신 이메일 오류 / 44101 포인트 부족", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "47401 상품을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "47901 동일 요청 식별자가 다른 구매 내용에 재사용됨 / 40900 포인트 잔액 동시성 충돌", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "44150 포인트 지갑 생성 실패 / 50000 차감 포인트 계산 불일치·구매 저장 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/purchases/confirm")
    public ApiResponse<GifticonPurchaseConfirmResponse> confirm(
            @UserId Long userId,
            @RequestBody @Valid ConfirmGifticonPurchaseRequest req
    ) {
        return ApiResponse.success(purchaseService.confirm(userId, req));
    }
}
