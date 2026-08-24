package CamNecT.server.domain.profile.components.institutions.controller;

import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.common.response.ErrorResponse;
import CamNecT.server.domain.profile.components.institutions.dto.InstitutionListResponse;
import CamNecT.server.domain.profile.components.institutions.dto.InstitutionResponse;
import CamNecT.server.domain.profile.components.majors.dto.MajorListResponse;
import CamNecT.server.domain.profile.components.majors.dto.MajorResponse;
import CamNecT.server.domain.profile.components.institutions.service.InstitutionService;
import CamNecT.server.domain.profile.components.majors.service.MajorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Institution", description = "학교 및 전공 정보 관련 API")
@RestController
@RequestMapping("/api/institutions")
@RequiredArgsConstructor
@Validated
public class InstitutionController {

    private final InstitutionService institutionService;
    private final MajorService majorService;

    @Operation(
            summary = "대학 검색",
            description = "키워드로 대학을 검색합니다. 키워드가 없거나 공백일 경우 빈 리스트를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 검색어가 100자를 초과함", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 학교 검색 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ApiResponse<InstitutionListResponse> searchInstitutions(
            @Parameter(description = "검색할 대학 이름 키워드 (예: 건국 / Konkuk)")
            @RequestParam(required = false) @Size(max = 100) String keyword
    ) {
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.success(InstitutionListResponse.empty());
        }
        return ApiResponse.success(institutionService.searchInstitutions(keyword));
    }

    @Operation(
            summary = "대학 단건 조회",
            description = "ID로 특정 대학의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 institutionId 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44407 학교를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 학교 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{institutionId}")
    public ApiResponse<InstitutionResponse> getInstitution(
            @Parameter(description = "조회할 대학 ID", required = true)
            @PathVariable @Positive Long institutionId
    ) {
        return ApiResponse.success(institutionService.getInstitution(institutionId));
    }

    @Operation(
            summary = "특정 대학의 전공 목록 조회",
            description = "특정 대학에 개설된 모든 전공 리스트를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 institutionId 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44407 학교를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 전공 목록 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{institutionId}/majors")
    public ApiResponse<MajorListResponse> getMajors(
            @Parameter(description = "전공을 조회할 대학 ID", required = true)
            @PathVariable @Positive Long institutionId
    ) {
        return ApiResponse.success(majorService.getMajors(institutionId));
    }

    @Operation(
            summary = "전공 단건 조회",
            description = "특정 대학의 특정 전공 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 institutionId 또는 majorId 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44407 학교 / 44408 전공을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 전공 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{institutionId}/majors/{majorId}")
    public ApiResponse<MajorResponse> getMajor(
            @Parameter(description = "대학 ID", required = true)
            @PathVariable @Positive Long institutionId,
            @Parameter(description = "조회할 전공 ID", required = true)
            @PathVariable @Positive Long majorId
    ) {
        return ApiResponse.success(majorService.getMajor(institutionId, majorId));
    }
}
