package CamNecT.server.domain.chat.controller;

import CamNecT.server.domain.chat.dto.request.request.ChatRequestAcceptDto;
import CamNecT.server.domain.chat.dto.request.response.ChatRequestDetailDto;
import CamNecT.server.domain.chat.dto.request.response.ChatRequestListResponseDto;
import CamNecT.server.domain.chat.dto.request.request.ChatRequestSendDto;
import CamNecT.server.domain.chat.model.ChatRequest;
import CamNecT.server.domain.chat.service.ChatService;
import CamNecT.server.global.common.auth.UserId;
import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.common.response.ErrorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/request")
@Tag(name = "Chat Request", description = "커피챗 요청/응답 API")
@Validated
public class ChatRequestController {

    private final ChatService chatService;

    @Operation(summary = "커피챗 요청 보내기 (커피챗)", description = "상대방에게 커피챗 요청을 보냅니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 수신자·태그·내용 검증 실패 / 48001 자기 자신에게 요청 / 48004 상대방이 커피챗 요청을 받지 않는 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "48403 태그 / 48405 수신자를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "48901 대기 중인 중복 요청 / 48902 이미 승인된 채팅방 관계", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 요청·알림 저장 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/send")
    public ApiResponse<Long> sendRequest(
            @UserId Long userId,
            @RequestBody @Valid ChatRequestSendDto request
    ) {
        return ApiResponse.success(
                chatService.sendCoffeeChatRequest(userId, request.receiverId(),
                        request.tagIds(), request.content())
        );
    }

    @Operation(summary = "커피챗 요청 수락/거절", description = "받은 커피챗 요청을 수락하거나 거절합니다. 수락 시 채팅방이 생성됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청 ID·응답값 검증 실패 / 48002 이미 반대 상태로 처리되었거나 종료된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자 / 48301 요청 수신자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "48401 요청을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 채팅방·포인트·알림 처리 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/respond")
    public ApiResponse<Void> respondRequest(
            @UserId Long userId,
            @RequestBody @Valid ChatRequestAcceptDto response
    ) {
        chatService.respondToRequest(
                response.requestId(),
                userId,
                response.isAccepted()
        );

        return ApiResponse.success(null);
    }

    @Operation(summary = "커피챗 요청 목록 조회", description = "요청 타입(COFFEE_CHAT, TEAM_RECRUIT)에 따라 받은 요청 리스트를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 type 누락 또는 허용되지 않은 enum 값", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 요청·프로필·모집글 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/list")
    public ApiResponse<ChatRequestListResponseDto> getRequestList(
            @UserId Long userId,
            @RequestParam("type") ChatRequest.RequestType type
    ) {
        ChatRequestListResponseDto response = chatService.getChatRequestList(userId, type);
        return ApiResponse.success(response);
    }

    @Operation(summary = "커피챗 요청 상세 조회", description = "특정 커피챗 요청의 상세 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 요청 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자 / 48301 요청 당사자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "48401 요청을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 요청·프로필·태그 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{requestId}")
    public ApiResponse<ChatRequestDetailDto> getRequestDetail(
            @UserId Long userId,
            @PathVariable @Positive Long requestId
    ) {
        log.info("커피챗 요청 상세 조회 - 요청자 ID: {}, 요청서 ID: {}", userId, requestId);

        ChatRequestDetailDto detail = chatService.getChatRequestDetail(requestId, userId);

        return ApiResponse.success(detail);
    }

    @Operation(summary = "커피챗 요청 전체 삭제", description = "내가 받은 모든 커피챗 요청(COFFEE_CHAT)을 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 대기 요청 일괄 거절 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/all/coffee-chat")
    public ApiResponse<Void> deleteAllCoffeeChatRequests(@UserId Long userId) {
        chatService.rejectAllCoffeeChatRequests(userId, ChatRequest.RequestType.COFFEE_CHAT);
        return ApiResponse.success(null);
    }

    @Operation(summary = "팀원 모집 요청 전체 삭제 (게시글별)", description = "특정 팀원모집글(Recruitment)과 관련된 모든 팀원 모집 요청을 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 모집글 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 팀원 모집 대기 요청 일괄 거절 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/all/team-recruit/{recruitmentId}")
    public ApiResponse<Void> deleteAllTeamRecruitRequests(
            @UserId Long userId,
            @PathVariable @Positive Long recruitmentId
    ) {
        chatService.rejectAllTeamRecruitRequestsByRecruitment(userId, recruitmentId);
        return ApiResponse.success(null);
    }
}
