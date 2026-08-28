package CamNecT.server.domain.chat.service;

import CamNecT.server.domain.activity.repository.recruitment.TeamRecruitmentRepository;
import CamNecT.server.domain.chat.dto.message.ChatMessageSendRequestDto;
import CamNecT.server.domain.chat.dto.message.ChatMessageAckResponseDto;
import CamNecT.server.domain.chat.dto.request.response.ChatRequestDetailDto;
import CamNecT.server.domain.chat.dto.request.response.ChatRequestListResponseDto;
import CamNecT.server.domain.chat.dto.room.ChatRoomWithDetailDto;
import CamNecT.server.domain.chat.event.ChatMessageCommittedEvent;
import CamNecT.server.domain.chat.event.ChatRoomClosedCommittedEvent;
import CamNecT.server.domain.chat.model.Chat;
import CamNecT.server.domain.chat.model.ChatRequest;
import CamNecT.server.domain.chat.model.ChatRoom;
import CamNecT.server.domain.chat.repository.ChatRepository;
import CamNecT.server.domain.chat.repository.ChatRequestRepository;
import CamNecT.server.domain.chat.repository.ChatRoomRepository;
import CamNecT.server.domain.profile.components.majors.repository.MajorRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.users.repository.UserTagMapRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.CoffeeChatErrorCode;
import CamNecT.server.global.point.service.PointService;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import CamNecT.server.global.tag.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock UserRepository userRepository;
    @Mock ChatRepository chatRepository;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatRequestRepository chatRequestRepository;
    @Mock TagRepository tagRepository;
    @Mock UserProfileRepository userProfileRepository;
    @Mock UserTagMapRepository userTagMapRepository;
    @Mock MajorRepository majorRepository;
    @Mock TeamRecruitmentRepository recruitmentRepository;
    @Mock PublicUrlIssuer publicUrlIssuer;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ChatPresenceService presenceService;
    @Mock PointService pointService;
    @Mock AccountAccessGuard accountAccessGuard;

    @InjectMocks ChatService chatService;

    @Test
    void closePublishesRoomClosedEventAfterChangingRoomAndRequestState() {
        Users user = activeUser(1L);
        ChatRoom room = mock(ChatRoom.class);
        ChatRequest request = mock(ChatRequest.class);
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(user);
        when(chatRoomRepository.findByUserIdWithDetailsForUpdate(99L, 1L))
                .thenReturn(Optional.of(room));
        when(room.getRequest()).thenReturn(request);

        chatService.closeChatRoom(99L, 1L);

        InOrder inOrder = inOrder(accountAccessGuard, chatRoomRepository, room, request, eventPublisher);
        inOrder.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        inOrder.verify(chatRoomRepository).findByUserIdWithDetailsForUpdate(99L, 1L);
        inOrder.verify(room).closeRoom();
        inOrder.verify(request).closeRequest();
        ArgumentCaptor<ChatRoomClosedCommittedEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatRoomClosedCommittedEvent.class);
        inOrder.verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().closedEvent().type()).isEqualTo("ROOM_CLOSED");
        assertThat(eventCaptor.getValue().closedEvent().roomId()).isEqualTo(99L);
    }

    @Test
    void exitPublishesRoomClosedEventBecauseLeavingClosesTheRoom() {
        Users user = activeUser(1L);
        ChatRoom room = mock(ChatRoom.class);
        ChatRequest request = mock(ChatRequest.class);
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(user);
        when(chatRoomRepository.findByUserIdWithDetailsForUpdate(99L, 1L))
                .thenReturn(Optional.of(room));
        when(room.getRequest()).thenReturn(request);

        chatService.exitOfChatRoom(99L, 1L);

        InOrder inOrder = inOrder(accountAccessGuard, chatRoomRepository, room, request, eventPublisher);
        inOrder.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        inOrder.verify(chatRoomRepository).findByUserIdWithDetailsForUpdate(99L, 1L);
        inOrder.verify(room).leave(1L);
        inOrder.verify(request).closeRequest();
        ArgumentCaptor<ChatRoomClosedCommittedEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatRoomClosedCommittedEvent.class);
        inOrder.verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().closedEvent().type()).isEqualTo("ROOM_CLOSED");
        assertThat(eventCaptor.getValue().closedEvent().roomId()).isEqualTo(99L);
    }

    @Test
    void oversizedMessageIsRejectedBeforeDatabaseAccess() {
        ChatMessageSendRequestDto request = new ChatMessageSendRequestDto(
                99L,
                "x".repeat(ChatMessageSendRequestDto.MAX_CONTENT_LENGTH + 1),
                "0e9e31aa-99e7-4c58-90d8-f939b56fd234"
        );

        CustomException ex = assertThrows(CustomException.class,
                () -> chatService.sendMessage(1L, request));

        assertThat(ex.getErrorCode()).isEqualTo(CoffeeChatErrorCode.INVALID_CHAT_CONTENT);
        verifyNoInteractions(chatRoomRepository, chatRepository);
    }

    @Test
    void nonParticipantCannotSendMessageToKnownRoomId() {
        Users requester = activeUser(1L);
        Users receiver = activeUser(2L);
        Users attacker = activeUser(3L);
        ChatRoom room = mock(ChatRoom.class);

        when(chatRoomRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(room));
        when(room.getStatus()).thenReturn(ChatRoom.RoomStatus.OPEN);
        when(room.getRequester()).thenReturn(requester);
        when(room.getReceiver()).thenReturn(receiver);
        when(accountAccessGuard.requireAccessibleForUpdate(3L)).thenReturn(attacker);

        CustomException ex = assertThrows(CustomException.class,
                () -> chatService.sendMessage(3L, new ChatMessageSendRequestDto(
                        99L, "hello", "0e9e31aa-99e7-4c58-90d8-f939b56fd234")));

        assertThat(ex.getErrorCode()).isEqualTo(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED);
        InOrder accessOrder = inOrder(accountAccessGuard, chatRoomRepository);
        accessOrder.verify(accountAccessGuard).requireAccessibleForUpdate(3L);
        accessOrder.verify(chatRoomRepository).findByIdForUpdate(99L);
        verify(chatRepository, never()).save(any(Chat.class));
        verifyNoInteractions(presenceService, eventPublisher);
    }

    @Test
    void historyMarksCurrentUserMessagesReadNotOpponents() {
        Users reader = activeUser(1L);
        ChatRoom room = mock(ChatRoom.class);

        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(reader);
        when(chatRoomRepository.findByUserIdWithDetails(99L, 1L)).thenReturn(Optional.of(room));
        when(chatRepository.findUnreadMessages(99L, 1L)).thenReturn(List.of());
        when(chatRepository.findTop1000ByRoomId(eq(99L), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> chatService.getChatHistory(99L, 1L));

        InOrder accessOrder = inOrder(accountAccessGuard, chatRoomRepository, chatRepository);
        accessOrder.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        accessOrder.verify(chatRoomRepository).findByUserIdWithDetails(99L, 1L);
        accessOrder.verify(chatRepository).findUnreadMessages(99L, 1L);
        verify(accountAccessGuard, times(1)).requireAccessibleForUpdate(1L);
        verify(chatRepository).findUnreadMessages(99L, 1L);
        verify(chatRepository, never()).findUnreadMessages(99L, 2L);
    }

    @Test
    void contradictorySecondResponseIsRejectedButSameResponseIsIdempotent() {
        Users receiver = activeUser(2L);
        ChatRequest request = mock(ChatRequest.class);

        when(accountAccessGuard.requireAccessibleForUpdate(2L)).thenReturn(receiver);
        when(chatRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(request.getReceiver()).thenReturn(receiver);
        when(request.getStatus()).thenReturn(ChatRequest.RequestStatus.ACCEPTED);

        assertDoesNotThrow(() -> chatService.respondToRequest(10L, 2L, true));

        CustomException ex = assertThrows(CustomException.class,
                () -> chatService.respondToRequest(10L, 2L, false));
        assertThat(ex.getErrorCode()).isEqualTo(CoffeeChatErrorCode.REQUEST_ALREADY_PROCESSED);

        InOrder accessOrder = inOrder(accountAccessGuard, chatRequestRepository);
        accessOrder.verify(accountAccessGuard).requireAccessibleForUpdate(2L);
        accessOrder.verify(chatRequestRepository).findByIdForUpdate(10L);
        accessOrder.verify(accountAccessGuard).requireAccessibleForUpdate(2L);
        accessOrder.verify(chatRequestRepository).findByIdForUpdate(10L);
    }

    @Test
    void deletedRecruitmentUsesTheSameTitleInRequestDetailAndList() {
        Users receiver = activeUser(1L, "receiver");
        Users requester = activeUser(2L, "requester");
        ChatRequest request = mock(ChatRequest.class);
        java.time.LocalDateTime createdAt = java.time.LocalDateTime.of(2026, 8, 25, 12, 0);

        when(userRepository.findById(1L)).thenReturn(Optional.of(receiver));
        when(chatRequestRepository.findById(20L)).thenReturn(Optional.of(request));
        when(chatRequestRepository.findRequestsWithRequester(
                1L,
                ChatRequest.RequestType.TEAM_RECRUIT,
                ChatRequest.RequestStatus.WAITING
        )).thenReturn(List.of(request));
        when(request.getId()).thenReturn(20L);
        when(request.getRequester()).thenReturn(requester);
        when(request.getReceiver()).thenReturn(receiver);
        when(request.getType()).thenReturn(ChatRequest.RequestType.TEAM_RECRUIT);
        when(request.getRecruitmentId()).thenReturn(30L);
        when(request.getActivityId()).thenReturn(10L);
        when(request.getContent()).thenReturn("지원합니다.");
        when(request.getCreatedAt()).thenReturn(createdAt);
        when(request.getRequestInterests()).thenReturn(List.of());
        when(recruitmentRepository.findById(30L)).thenReturn(Optional.empty());
        when(recruitmentRepository.findAllById(Set.of(30L))).thenReturn(List.of());
        when(userProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(userProfileRepository.findGlobalsByUserIdIn(List.of(2L))).thenReturn(List.of());
        when(userProfileRepository.findAllByUserIdIn(List.of(2L))).thenReturn(List.of());
        when(userTagMapRepository.findAllTagsByUserId(2L)).thenReturn(List.of());

        ChatRequestDetailDto detail = chatService.getChatRequestDetail(20L, 1L);
        ChatRequestListResponseDto list = chatService.getChatRequestList(
                1L,
                ChatRequest.RequestType.TEAM_RECRUIT
        );

        assertThat(detail.recruitmentTitle()).isEqualTo("삭제된 모집 공고입니다.");
        assertThat(detail.recruitmentId()).isEqualTo(30L);
        assertThat(list.chatRequestList()).hasSize(1);
        assertThat(list.chatRequestList().getFirst().recruitmentTitle())
                .isEqualTo("삭제된 모집 공고입니다.");
        assertThat(list.chatRequestList().getFirst().recruitmentId()).isEqualTo(30L);
        verify(accountAccessGuard, never()).requireAccessibleForUpdate(anyLong());
    }

    @Test
    void deletedRecruitmentTitleAndIdArePreservedInAcceptedChatRoom() {
        Users me = activeUser(1L, "me");
        Users opponent = activeUser(2L, "opponent");
        ChatRequest request = mock(ChatRequest.class);
        ChatRoom room = mock(ChatRoom.class);

        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(me);
        when(chatRoomRepository.findByUserIdWithDetails(99L, 1L)).thenReturn(Optional.of(room));
        when(chatRepository.findUnreadMessages(99L, 1L)).thenReturn(List.of());
        when(chatRepository.findTop1000ByRoomId(eq(99L), any())).thenReturn(List.of());
        when(room.getId()).thenReturn(99L);
        when(room.getRequester()).thenReturn(me);
        when(room.getReceiver()).thenReturn(opponent);
        when(room.getRequest()).thenReturn(request);
        when(room.getStatus()).thenReturn(ChatRoom.RoomStatus.OPEN);
        when(room.getTags()).thenReturn(List.of());
        when(request.getType()).thenReturn(ChatRequest.RequestType.TEAM_RECRUIT);
        when(request.getRecruitmentId()).thenReturn(30L);
        when(request.getActivityId()).thenReturn(10L);
        when(request.getContent()).thenReturn("지원합니다.");
        when(recruitmentRepository.findById(30L)).thenReturn(Optional.empty());
        when(userProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(userTagMapRepository.findAllTagsByUserId(2L)).thenReturn(List.of());

        ChatRoomWithDetailDto result = chatService.getRoomWithDetails(99L, 1L);

        verify(accountAccessGuard, times(1)).requireAccessibleForUpdate(1L);

        assertThat(result.getRecruitmentTitle()).isEqualTo("삭제된 모집 공고입니다.");
        assertThat(result.getRecruitmentId()).isEqualTo(30L);
    }

    @Test
    void duplicateClientMessageIdReturnsExistingAckWithoutSideEffects() {
        String clientMessageId = "0e9e31aa-99e7-4c58-90d8-f939b56fd234";
        Users sender = activeUser(1L, "sender");
        Users receiver = activeUser(2L, "receiver");
        ChatRoom room = mock(ChatRoom.class);
        Chat existing = mock(Chat.class);

        when(chatRoomRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(99L);
        when(room.getStatus()).thenReturn(ChatRoom.RoomStatus.OPEN);
        when(room.getRequester()).thenReturn(sender);
        when(room.getReceiver()).thenReturn(receiver);
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(sender);
        when(chatRepository.findByClientMessageId(99L, 1L, clientMessageId))
                .thenReturn(Optional.of(existing));
        when(existing.getId()).thenReturn(501L);
        when(existing.getRoom()).thenReturn(room);
        when(existing.getSender()).thenReturn(sender);
        when(existing.getReceiver()).thenReturn(receiver);
        when(existing.getContent()).thenReturn("hello");
        when(existing.getClientMessageId()).thenReturn(clientMessageId);
        when(existing.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 7, 15, 10, 0));

        ChatMessageAckResponseDto ack = chatService.sendMessage(
                1L,
                new ChatMessageSendRequestDto(99L, "hello", clientMessageId)
        );

        assertThat(ack.messageId()).isEqualTo(501L);
        assertThat(ack.clientMessageId()).isEqualTo(clientMessageId);
        assertThat(ack.duplicate()).isTrue();
        verify(chatRepository, never()).save(any(Chat.class));
        verifyNoInteractions(presenceService, eventPublisher);
    }

    @Test
    void reusingClientMessageIdForDifferentContentIsRejected() {
        String clientMessageId = "0e9e31aa-99e7-4c58-90d8-f939b56fd234";
        Users sender = activeUser(1L, "sender");
        Users receiver = activeUser(2L, "receiver");
        ChatRoom room = mock(ChatRoom.class);
        Chat existing = mock(Chat.class);

        when(chatRoomRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(99L);
        when(room.getStatus()).thenReturn(ChatRoom.RoomStatus.OPEN);
        when(room.getRequester()).thenReturn(sender);
        when(room.getReceiver()).thenReturn(receiver);
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(sender);
        when(chatRepository.findByClientMessageId(99L, 1L, clientMessageId))
                .thenReturn(Optional.of(existing));
        when(existing.getContent()).thenReturn("first content");

        CustomException ex = assertThrows(CustomException.class, () -> chatService.sendMessage(
                1L,
                new ChatMessageSendRequestDto(99L, "different content", clientMessageId)
        ));

        assertThat(ex.getErrorCode()).isEqualTo(CoffeeChatErrorCode.IDEMPOTENCY_KEY_REUSED);
        verify(chatRepository, never()).save(any(Chat.class));
    }

    @Test
    void newMessagePersistsClientMessageIdAndReturnsAck() {
        String clientMessageId = "0e9e31aa-99e7-4c58-90d8-f939b56fd234";
        Users sender = activeUser(1L, "sender");
        Users receiver = activeUser(2L, "receiver");
        ChatRoom room = mock(ChatRoom.class);

        when(chatRoomRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(99L);
        when(room.getStatus()).thenReturn(ChatRoom.RoomStatus.OPEN);
        when(room.getRequester()).thenReturn(sender);
        when(room.getReceiver()).thenReturn(receiver);
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(sender);
        when(chatRepository.findByClientMessageId(99L, 1L, clientMessageId))
                .thenReturn(Optional.empty());
        when(presenceService.isPresent(99L, 2L)).thenReturn(true);

        ChatMessageAckResponseDto ack = chatService.sendMessage(
                1L,
                new ChatMessageSendRequestDto(99L, "hello", clientMessageId)
        );

        var chatCaptor = org.mockito.ArgumentCaptor.forClass(Chat.class);
        verify(chatRepository).save(chatCaptor.capture());
        assertThat(chatCaptor.getValue().getClientMessageId()).isEqualTo(clientMessageId);
        assertThat(ack.clientMessageId()).isEqualTo(clientMessageId);
        assertThat(ack.duplicate()).isFalse();
        InOrder accessOrder = inOrder(accountAccessGuard, chatRoomRepository);
        accessOrder.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        accessOrder.verify(chatRoomRepository).findByIdForUpdate(99L);
        verify(eventPublisher).publishEvent(any(ChatMessageCommittedEvent.class));
    }

    @Test
    void coffeeChatRequestLocksLowerReceiverBeforeRevalidatingHigherRequester() {
        Users requester = activeUser(2L, "requester");
        Users receiver = activeUser(1L, "receiver");
        ChatRequest savedRequest = mock(ChatRequest.class);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(receiver));
        when(accountAccessGuard.requireAccessibleForUpdate(2L)).thenReturn(requester);
        when(userProfileRepository.existsByUserIdAndOpenToCoffeeChatTrue(1L)).thenReturn(true);
        when(tagRepository.findAllById(List.of())).thenReturn(List.of());
        when(chatRequestRepository.save(any(ChatRequest.class))).thenReturn(savedRequest);
        when(savedRequest.getId()).thenReturn(10L);

        assertThat(chatService.sendCoffeeChatRequest(2L, 1L, List.of(), "안녕하세요"))
                .isEqualTo(10L);

        InOrder lockOrder = inOrder(userRepository, accountAccessGuard);
        lockOrder.verify(userRepository).findByIdForUpdate(1L);
        lockOrder.verify(accountAccessGuard).requireAccessibleForUpdate(2L);
        verify(accountAccessGuard, times(1)).requireAccessibleForUpdate(2L);
        verify(userRepository, never()).lockUserRow(anyLong());
    }

    @Test
    void inaccessibleRequesterKeepsPriorityOverMissingLowerReceiver() {
        CustomException denied = new CustomException(AuthErrorCode.USER_WITHDRAWN);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        when(accountAccessGuard.requireAccessibleForUpdate(2L)).thenThrow(denied);

        CustomException actual = assertThrows(
                CustomException.class,
                () -> chatService.sendCoffeeChatRequest(2L, 1L, List.of(), "안녕하세요")
        );

        assertThat(actual).isSameAs(denied);
        InOrder lockOrder = inOrder(userRepository, accountAccessGuard);
        lockOrder.verify(userRepository).findByIdForUpdate(1L);
        lockOrder.verify(accountAccessGuard).requireAccessibleForUpdate(2L);
    }

    @Test
    void inaccessibleSenderIsRejectedBeforeTheChatRoomIsLocked() {
        CustomException denied = new CustomException(AuthErrorCode.USER_WITHDRAWN);
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenThrow(denied);

        CustomException actual = assertThrows(CustomException.class, () -> chatService.sendMessage(
                1L,
                new ChatMessageSendRequestDto(
                        99L, "hello", "0e9e31aa-99e7-4c58-90d8-f939b56fd234")
        ));

        assertThat(actual).isSameAs(denied);
        verifyNoInteractions(chatRoomRepository, chatRepository);
    }

    @Test
    void rejectAllRevalidatesActorBeforeLoadingRequestsToChange() {
        Users actor = activeUser(1L);
        ChatRequest pending = mock(ChatRequest.class);
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(actor);
        when(chatRequestRepository.findAllByReceiver_UserIdAndTypeAndStatus(
                1L, ChatRequest.RequestType.COFFEE_CHAT, ChatRequest.RequestStatus.WAITING
        )).thenReturn(List.of(pending));

        chatService.rejectAllCoffeeChatRequests(1L, ChatRequest.RequestType.COFFEE_CHAT);

        InOrder accessOrder = inOrder(accountAccessGuard, chatRequestRepository, pending);
        accessOrder.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        accessOrder.verify(chatRequestRepository).findAllByReceiver_UserIdAndTypeAndStatus(
                1L, ChatRequest.RequestType.COFFEE_CHAT, ChatRequest.RequestStatus.WAITING);
        accessOrder.verify(pending).reject();
    }

    @Test
    void roomConstraintFailureIsPropagatedWithoutRollbackOnlyRecoveryQuery() {
        Users requester = activeUser(1L);
        Users receiver = activeUser(2L);
        ChatRequest request = mock(ChatRequest.class);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("duplicate request room");

        when(accountAccessGuard.requireAccessibleForUpdate(2L)).thenReturn(receiver);
        when(chatRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(request.getId()).thenReturn(10L);
        when(request.getRequester()).thenReturn(requester);
        when(request.getReceiver()).thenReturn(receiver);
        when(request.getStatus()).thenReturn(ChatRequest.RequestStatus.WAITING);
        when(chatRoomRepository.findByRequest_Id(10L)).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenThrow(failure);

        DataIntegrityViolationException actual = assertThrows(
                DataIntegrityViolationException.class,
                () -> chatService.respondToRequest(10L, 2L, true)
        );

        assertThat(actual).isSameAs(failure);
        verify(chatRoomRepository, times(1)).findByRequest_Id(10L);
        verifyNoInteractions(pointService);
    }

    private Users activeUser(Long userId) {
        return Users.builder().userId(userId).status(UserStatus.ACTIVE).build();
    }

    private Users activeUser(Long userId, String name) {
        return Users.builder().userId(userId).name(name).status(UserStatus.ACTIVE).build();
    }
}
