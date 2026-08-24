package CamNecT.server.global.tag.controller;

import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.tag.dto.TagGraphBatchResult;
import CamNecT.server.global.tag.service.TagGraphBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Tag Graph", description = "태그 연관성 그래프 배치 수동 실행")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/tag-graph")
public class AdminTagGraphController {

    private final TagGraphBatchService tagGraphBatchService;

    @Operation(summary = "태그 연관성 배치 수동 실행", description = "tag_stats/tag_relation을 최신 데이터로 재계산합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true)
    @PostMapping()
    public ApiResponse<TagGraphBatchResult> rebuild() {
        return ApiResponse.success(tagGraphBatchService.runNow());
    }
}
