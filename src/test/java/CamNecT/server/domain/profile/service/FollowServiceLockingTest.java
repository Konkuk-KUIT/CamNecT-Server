package CamNecT.server.domain.profile.service;

import CamNecT.server.domain.users.model.UserFollow;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserFollowRepository;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceLockingTest {

    @Mock UserFollowRepository followRepository;
    @Mock UserRepository userRepository;
    @Mock AccountAccessGuard accountAccessGuard;
    @Mock UserProfileRepository userProfileRepository;
    @Mock PublicUrlIssuer publicUrlIssuer;

    @InjectMocks FollowService followService;

    @Test
    void followLocksBothUsersByAscendingPrimaryKeyBeforeSaving() {
        Users follower = user(10L, UserStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(user(2L, UserStatus.ACTIVE)));
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(follower));
        when(accountAccessGuard.requireAccessibleForUpdate(10L)).thenReturn(follower);

        followService.follow(10L, 2L);

        InOrder order = inOrder(userRepository, accountAccessGuard, followRepository);
        order.verify(userRepository).findByIdForUpdate(2L);
        order.verify(userRepository).findByIdForUpdate(10L);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(10L);
        ArgumentCaptor<UserFollow> followCaptor = ArgumentCaptor.forClass(UserFollow.class);
        order.verify(followRepository).saveAndFlush(followCaptor.capture());
        assertThat(followCaptor.getValue().getFollowerId()).isEqualTo(10L);
        assertThat(followCaptor.getValue().getFollowingId()).isEqualTo(2L);
    }

    @Test
    void followDoesNotRecreateRelationshipForWithdrawnTarget() {
        Users follower = user(1L, UserStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(follower));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(user(2L, UserStatus.WITHDRAWN)));
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(follower);

        CustomException exception = assertThrows(CustomException.class, () -> followService.follow(1L, 2L));

        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        verify(followRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unfollowRevalidatesActorBeforeReadingTheRelationship() {
        Users follower = user(1L, UserStatus.ACTIVE);
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(follower);
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);

        followService.unfollow(1L, 2L);

        InOrder order = inOrder(accountAccessGuard, followRepository);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        order.verify(followRepository).existsByFollowerIdAndFollowingId(1L, 2L);
        order.verify(followRepository).deleteByFollowerIdAndFollowingId(1L, 2L);
    }

    private Users user(Long userId, UserStatus status) {
        return Users.builder().userId(userId).status(status).build();
    }
}
