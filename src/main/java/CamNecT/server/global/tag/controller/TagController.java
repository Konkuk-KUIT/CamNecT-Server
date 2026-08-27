package CamNecT.server.global.tag.controller;

import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.tag.dto.TagCategoryDto;
import CamNecT.server.global.tag.model.enums.TagScope;
import CamNecT.server.global.tag.service.TagQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Tags", description = "태그/카테고리 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tags")
public class TagController {
    private final TagQueryService tagQueryService;

    @Operation(summary = "태그 카테고리/태그 목록 조회",
            description = """
                    온보딩/프로필 태그 선택에 필요한 카테고리별 태그 목록을 반환합니다.
                    scope=COMMUNITY_QUESTION 이면 '채택 상태' 카테고리 추가,
                    scope=ACTIVITY_RECRUIT 이면 '모집 상태' 카테고리 추가.
                    """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true)
    @GetMapping
    public ApiResponse<List<TagCategoryDto>> list(
            @RequestParam(required = false) TagScope scope
    ) {
        return ApiResponse.success(tagQueryService.listCategoriesWithTags(scope));
    }
}
