package CamNecT.server.domain.chat.service;

import CamNecT.server.domain.activity.repository.recruitment.TeamRecruitmentRepository;
import CamNecT.server.domain.chat.dto.message.ChatReadEvent;
import CamNecT.server.domain.chat.event.ChatReadCommittedEvent;
import CamNecT.server.domain.chat.model.Chat;
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
import CamNecT.server.global.point.service.PointService;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import CamNecT.server.global.tag.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatReadStateTest {

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
    void markAllAsReadPublishesGreatestOrderedMessageIdAndVisibleUnreadTotal() {
        Users reader = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        Chat older = mock(Chat.class);
        Chat newer = mock(Chat.class);
        when(newer.getId()).thenReturn(20L);
        when(newer.getContent()).thenReturn("newer");
        when(newer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 1, 1, 10, 1));

        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(reader);
        when(chatRoomRepository.existsAccessibleByUserId(99L, 1L)).thenReturn(true);
        when(chatRepository.findUnreadMessages(99L, 1L)).thenReturn(List.of(older, newer));
        chatService.markAllAsRead(99L, reader);

        ArgumentCaptor<ChatReadCommittedEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatReadCommittedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ChatReadCommittedEvent event = eventCaptor.getValue();
        ChatReadEvent readEvent = event.readEvent();
        assertThat(readEvent.getLastReadMessageId()).isEqualTo(20L);
        assertThat(event.readerId()).isEqualTo(1L);
        assertThat(event.lastMessage()).isEqualTo("newer");
    }

    @Test
    void staleReaderEntityIsRevalidatedByIdBeforeReadStateChanges() {
        Users staleReader = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        CustomException denied = new CustomException(AuthErrorCode.USER_WITHDRAWN);
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenThrow(denied);

        CustomException actual = assertThrows(
                CustomException.class,
                () -> chatService.markAllAsRead(99L, staleReader)
        );

        assertThat(actual).isSameAs(denied);
        verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        verifyNoInteractions(chatRoomRepository, chatRepository, eventPublisher);
    }
}
