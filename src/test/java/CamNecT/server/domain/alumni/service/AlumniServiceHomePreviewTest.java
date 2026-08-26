package CamNecT.server.domain.alumni.service;

import CamNecT.server.domain.alumni.repository.AlumniRepository;
import CamNecT.server.domain.chat.repository.ChatRoomRepository;
import CamNecT.server.domain.home.dto.HomeResponse;
import CamNecT.server.domain.users.model.UserProfile;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.users.repository.UserTagMapRepository;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlumniServiceHomePreviewTest {

    @Mock UserTagMapRepository userTagMapRepository;
    @Mock UserProfileRepository userProfileRepository;
    @Mock UserRepository userRepository;
    @Mock AlumniRepository alumniRepository;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock PublicUrlIssuer publicUrlIssuer;

    @InjectMocks AlumniService alumniService;

    @Test
    void requestsOnlyLimitPlusRepositoryLookaheadAndReportsHasMore() {
        Long myId = 1L;
        List<Long> repositoryIds = List.of(2L, 3L, 4L, 5L, 6L, 7L);
        List<Long> previewIds = repositoryIds.subList(0, 5);
        when(alumniRepository.findAlumniIdsByConditions(
                eq(myId), isNull(), eq(List.of()), any(Pageable.class)))
                .thenReturn(repositoryIds);
        when(userProfileRepository.findAllByUserIdInWithUser(previewIds))
                .thenReturn(previewIds.stream().map(this::profile).toList());
        when(userTagMapRepository.findTagNamesWithUserIdByUserIdIn(previewIds))
                .thenReturn(List.of());

        HomeResponse.AlumniSection result = alumniService.getHomePreview(myId, 5);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(alumniRepository).findAlumniIdsByConditions(
                eq(myId), isNull(), eq(List.of()), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.items()).hasSize(5);
        assertThat(result.items()).extracting(item -> item.userId()).containsExactlyElementsOf(previewIds);
    }

    @Test
    void reportsNoMoreWhenRepositoryReturnsExactlyTheLimit() {
        Long myId = 1L;
        List<Long> previewIds = List.of(2L, 3L, 4L, 5L, 6L);
        when(alumniRepository.findAlumniIdsByConditions(
                eq(myId), isNull(), eq(List.of()), any(Pageable.class)))
                .thenReturn(previewIds);
        when(userProfileRepository.findAllByUserIdInWithUser(previewIds))
                .thenReturn(previewIds.stream().map(this::profile).toList());
        when(userTagMapRepository.findTagNamesWithUserIdByUserIdIn(previewIds))
                .thenReturn(List.of());

        HomeResponse.AlumniSection result = alumniService.getHomePreview(myId, 5);

        assertThat(result.hasMore()).isFalse();
        assertThat(result.items()).hasSize(5);
    }

    private UserProfile profile(Long userId) {
        Users user = Users.builder()
                .userId(userId)
                .name("alumni-" + userId)
                .build();
        return UserProfile.builder()
                .userId(userId)
                .user(user)
                .build();
    }
}
