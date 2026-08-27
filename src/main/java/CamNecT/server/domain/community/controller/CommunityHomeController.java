package CamNecT.server.domain.community.controller;

import CamNecT.server.domain.community.dto.response.CommunityHomeResponse;
import CamNecT.server.domain.community.service.CommunityHomeService;
import CamNecT.server.global.common.auth.UserId;
import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.common.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Community Home", description = "커뮤니티 홈 화면 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/community")
public class CommunityHomeController {

    private final CommunityHomeService communityHomeService;

    @Operation(
            summary = "커뮤니티 홈 화면 데이터 조회",
            description = "커뮤니티 홈에서 보여줄 인기글, 최신글 등을 통합 조회합니다. 특정 태그(tagId)를 전달하면 해당 태그 위주의 맞춤형 데이터를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/home")
    public ApiResponse<CommunityHomeResponse> home(@UserId Long userId) {
        return ApiResponse.success(communityHomeService.getHome(userId));
    }
}
