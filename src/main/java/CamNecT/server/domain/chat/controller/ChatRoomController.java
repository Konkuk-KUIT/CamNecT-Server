package CamNecT.server.domain.chat.controller;

import CamNecT.server.domain.chat.dto.room.ChatRoomListDetailDto;
import CamNecT.server.domain.chat.dto.room.ChatRoomListResponseDto;
import CamNecT.server.domain.chat.dto.room.ChatRoomWithDetailDto;
import CamNecT.server.domain.chat.model.ChatRequest;
import CamNecT.server.domain.chat.repository.ChatRequestRepository;
import CamNecT.server.domain.chat.service.ChatService;
import CamNecT.server.global.common.auth.UserId;
import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.common.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
@Tag(name = "Chat Room", description = "채팅방 조회 및 입장 API")
@Validated
public class ChatRoomController {

    private final ChatService chatService;
    private final ChatRequestRepository chatRequestRepository;

    @Operation(summary = "내 채팅방 목록 조회", description = "참여 중인 모든 채팅방 목록과 전체 안 읽은 메시지 수를 반환합니다. 요청 타입(COFFEE_CHAT, TEAM_RECRUIT)의 리스트를 조회합니다. 아무것도 넘기지 않을 시 전체 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 허용되지 않은 type enum 값", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 채팅방·미읽음·상대 프로필 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/rooms")
    @Transactional(readOnly = true)
    public ApiResponse<ChatRoomListResponseDto> roomList(
            @UserId Long userId,
            @RequestParam(name = "type", required = false) ChatRequest.RequestType type
    ) {

        List<ChatRoomListDetailDto> roomList = chatService.getChatRoomList(userId, type);
        long totalUnreadCount = roomList.stream().mapToLong(ChatRoomListDetailDto::getUnreadCount).sum();
        ChatRequest.RequestStatus pending = ChatRequest.RequestStatus.WAITING;
        boolean requestExists = chatRequestRepository.existsReceivedPending(userId, pending, type);

        ChatRoomListResponseDto response = ChatRoomListResponseDto.builder()
                .chatRoomList(roomList)
                .totalUnreadCount(totalUnreadCount)
                .requestExists(requestExists)
                .build();

        return ApiResponse.success(response);
    }


    @Operation(summary = "채팅방 상세 조회 (입장)", description = "특정 채팅방의 상대방 정보와 채팅 내역을 불러옵니다. 이 API 호출 후 소켓 연결(CONNECT)을 진행해야 합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 채팅방 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "48402 채팅방이 없거나 참여·조회 가능한 사용자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 채팅 내역 읽음 처리·프로필 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/room/{roomId}")
    public ApiResponse<ChatRoomWithDetailDto> joinRoom(@Parameter(description = "채팅방 ID") @PathVariable @Positive Long roomId, @UserId Long userId) {

        ChatRoomWithDetailDto response = chatService.getRoomWithDetails(roomId, userId);

        return ApiResponse.success(response);
    }

    @Operation(summary = "채팅방 종료", description = "해당 채팅방을 종료합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 채팅방 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "48402 채팅방이 없거나 참여자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 채팅방 종료 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/room/{roomId}/close")
    public ApiResponse<Void> closeRoom(@Parameter(description = "채팅방 ID") @PathVariable @Positive Long roomId, @UserId Long userId) {
        chatService.closeChatRoom(roomId, userId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "채팅방 개별 나가기 (퇴장)", description = "해당 채팅방을 나갑니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 채팅방 ID 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 오류 또는 토큰 사용자 없음 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "48402 채팅방이 없거나 참여자가 아님 / 48404 연결된 요청 정보 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 채팅방 퇴장·요청 종료 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/room/{roomId}/exit")
    public ApiResponse<Void> exitRoom(@Parameter(description = "채팅방 ID") @PathVariable @Positive Long roomId, @UserId Long userId) {
        chatService.exitOfChatRoom(roomId, userId);
        return ApiResponse.success(null);
    }
}
