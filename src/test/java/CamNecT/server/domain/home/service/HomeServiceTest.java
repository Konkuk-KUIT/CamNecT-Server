package CamNecT.server.domain.home.service;

import CamNecT.server.domain.activity.service.ActivityService;
import CamNecT.server.domain.alumni.service.AlumniService;
import CamNecT.server.domain.chat.service.ChatService;
import CamNecT.server.domain.home.dto.HomeResponse;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.point.repository.PointWalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock UserRepository userRepository;
    @Mock ChatService chatService;
    @Mock PointWalletRepository pointWalletRepository;
    @Mock AlumniService alumniService;
    @Mock ActivityService activityService;

    @InjectMocks HomeService homeService;

    @Test
    void requestsFiveAlumniForHomePreview() {
        Long userId = 1L;
        Users user = Users.builder().userId(userId).name("홈 사용자").build();
        HomeResponse.AlumniSection alumni = HomeResponse.AlumniSection.empty();

        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));
        when(chatService.getHomeInbox(userId, 2)).thenReturn(HomeResponse.CoffeeChatSection.empty());
        when(chatService.getHomeRecruitmentInbox(userId, 5)).thenReturn(HomeResponse.RecruitmentSection.empty());
        when(pointWalletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(alumniService.getHomePreview(userId, 5)).thenReturn(alumni);
        when(activityService.getHomeContests(4)).thenReturn(HomeResponse.ContestSection.empty());

        HomeResponse response = homeService.getHome(userId);

        assertThat(response.alumni()).isSameAs(alumni);
        verify(alumniService).getHomePreview(userId, 5);
    }
}
