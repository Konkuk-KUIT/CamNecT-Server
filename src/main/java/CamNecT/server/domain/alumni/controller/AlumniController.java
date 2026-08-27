package CamNecT.server.domain.alumni.controller;

import CamNecT.server.domain.alumni.dto.response.AlumniPreviewResponse;
import CamNecT.server.domain.alumni.service.AlumniService;
import CamNecT.server.global.common.auth.UserId;
import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.common.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Alumni", description = "동문 탐색 및 조회 관련 API")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/alumni")
public class AlumniController {

    private final AlumniService alumniService;

    @Operation(
            summary = "동문 목록 검색 및 조회",
            description = "이름 또는 관심 태그를 필터로 사용하여 동문 목록을 조회합니다. 필터 값이 없으면 추천순 동문 목록을 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 태그·page·size 쿼리 형식 오류 또는 page는 0 이상, size는 1~50 범위가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 동문·프로필·태그 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ApiResponse<Slice<AlumniPreviewResponse>> searchAlumni(
            @UserId Long userId,
            @RequestParam(required = false) @Size(max = 100) String name,
            @RequestParam(required = false) List<@Positive Long> tags,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(alumniService.searchAlumni(userId, name, tags, pageable));
    }
}
