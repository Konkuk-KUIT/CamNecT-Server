package CamNecT.server.domain.chat.service;

import CamNecT.server.domain.activity.repository.recruitment.TeamRecruitmentRepository;
import CamNecT.server.domain.chat.dto.message.ChatMessageSendRequestDto;
import CamNecT.server.domain.chat.dto.message.ChatMessageAckResponseDto;
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
import CamNecT.server.global.common.exception.CustomException;
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

import java.util.List;
import java.util.Optional;

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

    @InjectMocks ChatService chatService;

    @Test
    void closePublishesRoomClosedEventAfterChangingRoomAndRequestState() {
        Users user = activeUser(1L);
        ChatRoom room = mock(ChatRoom.class);
        ChatRequest request = mock(ChatRequest.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatRoomRepository.findByUserIdWithDetailsForUpdate(99L, 1L))
                .thenReturn(Optional.of(room));
        when(room.getRequest()).thenReturn(request);

        chatService.closeChatRoom(99L, 1L);

        InOrder inOrder = inOrder(room, request, eventPublisher);
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatRoomRepository.findByUserIdWithDetailsForUpdate(99L, 1L))
                .thenReturn(Optional.of(room));
        when(room.getRequest()).thenReturn(request);

        chatService.exitOfChatRoom(99L, 1L);

        verify(room).leave(1L);
        verify(request).closeRequest();
        verify(eventPublisher).publishEvent(any(ChatRoomClosedCommittedEvent.class));
    }

    @Test
    void completeExitClosesAndLeavesThenPublishesRoomClosedEvent() {
        Users user = activeUser(1L);
        ChatRoom room = mock(ChatRoom.class);
        ChatRequest request = mock(ChatRequest.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatRoomRepository.findByUserIdWithDetailsForUpdate(99L, 1L))
                .thenReturn(Optional.of(room));
        when(room.getRequest()).thenReturn(request);

        chatService.completeExitChatRoom(99L, 1L);

        InOrder inOrder = inOrder(room, request, eventPublisher);
        inOrder.verify(room).leave(1L);
        inOrder.verify(request).closeRequest();
        inOrder.verify(room).closeRoom();
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
        when(userRepository.findById(3L)).thenReturn(Optional.of(attacker));

        CustomException ex = assertThrows(CustomException.class,
                () -> chatService.sendMessage(3L, new ChatMessageSendRequestDto(
                        99L, "hello", "0e9e31aa-99e7-4c58-90d8-f939b56fd234")));

        assertThat(ex.getErrorCode()).isEqualTo(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED);
        verify(chatRepository, never()).save(any(Chat.class));
        verifyNoInteractions(presenceService, eventPublisher);
    }

    @Test
    void historyMarksCurrentUserMessagesReadNotOpponents() {
        Users reader = activeUser(1L);
        ChatRoom room = mock(ChatRoom.class);

        when(userRepository.findById(1L)).thenReturn(Optional.of(reader));
        when(chatRoomRepository.findByUserIdWithDetails(99L, 1L)).thenReturn(Optional.of(room));
        when(chatRoomRepository.existsAccessibleByUserId(99L, 1L)).thenReturn(true);
        when(chatRepository.findUnreadMessages(99L, 1L)).thenReturn(List.of());
        when(chatRepository.findTop1000ByRoomId(eq(99L), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> chatService.getChatHistory(99L, 1L));

        verify(chatRepository).findUnreadMessages(99L, 1L);
        verify(chatRepository, never()).findUnreadMessages(99L, 2L);
    }

    @Test
    void contradictorySecondResponseIsRejectedButSameResponseIsIdempotent() {
        Users receiver = activeUser(2L);
        ChatRequest request = mock(ChatRequest.class);

        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(chatRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(request.getReceiver()).thenReturn(receiver);
        when(request.getStatus()).thenReturn(ChatRequest.RequestStatus.ACCEPTED);

        assertDoesNotThrow(() -> chatService.respondToRequest(10L, 2L, true));

        CustomException ex = assertThrows(CustomException.class,
                () -> chatService.respondToRequest(10L, 2L, false));
        assertThat(ex.getErrorCode()).isEqualTo(CoffeeChatErrorCode.REQUEST_ALREADY_PROCESSED);
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
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
        verify(eventPublisher).publishEvent(any(ChatMessageCommittedEvent.class));
    }

    private Users activeUser(Long userId) {
        return Users.builder().userId(userId).status(UserStatus.ACTIVE).build();
    }

    private Users activeUser(Long userId, String name) {
        return Users.builder().userId(userId).name(name).status(UserStatus.ACTIVE).build();
    }
}
