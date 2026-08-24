package CamNecT.server.global.notification.service;

import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.users.model.UserProfile;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.NotificationErrorCode;
import CamNecT.server.global.notification.model.NotificationType;
import CamNecT.server.global.notification.model.Notification;
import CamNecT.server.global.notification.repository.NotificationRepository;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserProfileRepository userProfileRepository = mock(UserProfileRepository.class);
    private final PublicUrlIssuer publicUrlIssuer = mock(PublicUrlIssuer.class);

    private final NotificationService notificationService = new NotificationService(
            notificationRepository,
            userRepository,
            userProfileRepository,
            publicUrlIssuer
    );

    @BeforeEach
    void setUpAuthenticatedUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                Users.builder().userId(1L).status(UserStatus.ACTIVE).build()
        ));
    }

    @Test
    void markReadTreatsAlreadyReadNotificationAsSuccess() {
        when(notificationRepository.existsByIdAndReceiverUserId(10L, 1L)).thenReturn(true);
        when(notificationRepository.markRead(1L, 10L)).thenReturn(0);

        assertDoesNotThrow(() -> notificationService.markRead(1L, 10L));

        verify(notificationRepository).markRead(1L, 10L);
    }

    @Test
    void markReadRejectsMissingOrOtherUsersNotification() {
        when(notificationRepository.existsByIdAndReceiverUserId(10L, 1L)).thenReturn(false);

        CustomException ex = assertThrows(
                CustomException.class,
                () -> notificationService.markRead(1L, 10L)
        );

        assertThat(ex.getErrorCode()).isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        verify(notificationRepository, never()).markRead(anyLong(), anyLong());
    }

    @Test
    void listNormalizesInvalidPageSizeToDefault() {
        when(notificationRepository.findByReceiverUserIdAndReadFalseAndTypeNotOrderByIdDesc(
                eq(1L),
                eq(NotificationType.CHAT_MESSAGE_RECEIVED),
                any(Pageable.class)
        )).thenReturn(new SliceImpl<>(List.of()));

        notificationService.list(1L, null, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByReceiverUserIdAndReadFalseAndTypeNotOrderByIdDesc(
                eq(1L),
                eq(NotificationType.CHAT_MESSAGE_RECEIVED),
                captor.capture()
        );
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void createTruncatesMessageWithoutSplittingUnicodeCodePoint() {
        String emoji = "😀";
        String message = emoji.repeat(256);

        notificationService.create(
                1L,
                2L,
                NotificationType.POST_COMMENTED,
                message,
                10L,
                20L
        );

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).isEqualTo(emoji.repeat(255));
        assertThat(captor.getValue().getMessage().codePointCount(
                0,
                captor.getValue().getMessage().length()
        )).isEqualTo(255);
    }

    @Test
    void listItemsKeepsDefaultImageWhenProfileKeyCannotBeIssued() {
        Notification notification = Notification.of(
                1L,
                2L,
                NotificationType.POST_COMMENTED,
                "message",
                10L,
                20L,
                null,
                "/community/post/10"
        );
        Users actor = Users.builder().userId(2L).name("actor").build();
        UserProfile profile = UserProfile.builder()
                .userId(2L)
                .profileImageKey("temp/profile.png")
                .build();

        when(notificationRepository.findByReceiverUserIdAndReadFalseAndTypeNotOrderByIdDesc(
                eq(1L),
                eq(NotificationType.CHAT_MESSAGE_RECEIVED),
                any(Pageable.class)
        )).thenReturn(new SliceImpl<>(List.of(notification)));
        when(userRepository.findAllById(any())).thenReturn(List.of(actor));
        when(userProfileRepository.findAllByUserIdIn(any())).thenReturn(List.of(profile));
        when(publicUrlIssuer.issuePublicUrl("temp/profile.png")).thenReturn(null);

        var result = notificationService.listItems(1L, null, 20);

        assertThat(result.getContent().getFirst().actorProfileImageUrl())
                .isEqualTo("/images/default.png");
    }
}
