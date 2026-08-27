package CamNecT.server.domain.profile.components.archive.controller;

import CamNecT.server.domain.profile.components.archive.dto.response.MyArchiveResponse;
import CamNecT.server.domain.profile.components.archive.service.ArchiveQueryService;
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
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@Tag(name = "Profile Archive", description = "내 작성글·북마크 아카이브 API")
@RequestMapping("/api/profile/bookmarks")
public class BookmarkController {

    private final ArchiveQueryService archiveQueryService;

    // ===== Bookmarks (3) =====
    @GetMapping("/community")
    @Operation(summary = "북마크한 커뮤니티 글 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 정렬값·커서·size 형식 오류 또는 size가 1~50 범위가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 커뮤니티 북마크 조회·조립 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<MyArchiveResponse> bookmarkedCommunity(
            @UserId Long userId,
            @RequestParam(defaultValue = "RECOMMENDED") MyArchiveResponse.Sort sort,
            @RequestParam(required = false) @Positive Long cursorId,
            @RequestParam(required = false) @PositiveOrZero Long cursorValue,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ApiResponse.success(
                archiveQueryService.getCommunityArchive(
                        userId,
                        MyArchiveResponse.ArchiveKind.BOOKMARKS,
                        sort,
                        cursorId,
                        cursorValue,
                        size
                )
        );
    }

    @GetMapping("/external")
    @Operation(summary = "북마크한 대외활동 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 정렬값·커서·size 형식 오류 또는 size가 1~50 범위가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 대외활동 북마크 조회·조립 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<MyArchiveResponse> bookmarkedExternal(
            @UserId Long userId,
            @RequestParam(defaultValue = "RECOMMENDED") MyArchiveResponse.Sort sort,
            @RequestParam(required = false) @Positive Long cursorId,
            @RequestParam(required = false) @PositiveOrZero Long cursorValue,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ApiResponse.success(
                archiveQueryService.getExternalArchive(
                        userId,
                        MyArchiveResponse.ArchiveKind.BOOKMARKS,
                        sort,
                        cursorId,
                        cursorValue,
                        size
                )
        );
    }

    @GetMapping("/recruitment")
    @Operation(summary = "북마크한 팀원 모집 글 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 정렬값·커서·size 형식 오류 또는 size가 1~50 범위가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 팀원 모집 북마크 조회·조립 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<MyArchiveResponse> bookmarkedRecruitment(
            @UserId Long userId,
            @RequestParam(defaultValue = "RECOMMENDED") MyArchiveResponse.Sort sort,
            @RequestParam(required = false) @Positive Long cursorId,
            @RequestParam(required = false) @PositiveOrZero Long cursorValue,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ApiResponse.success(
                archiveQueryService.getRecruitmentArchive(
                        userId,
                        MyArchiveResponse.ArchiveKind.BOOKMARKS,
                        sort,
                        cursorId,
                        cursorValue,
                        size
                )
        );
    }
}
