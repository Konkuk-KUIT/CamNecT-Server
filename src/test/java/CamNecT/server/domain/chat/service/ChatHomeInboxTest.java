package CamNecT.server.domain.chat.service;

import CamNecT.server.domain.chat.model.ChatRequest;
import CamNecT.server.domain.chat.repository.ChatRequestRepository;
import CamNecT.server.domain.home.dto.HomeResponse;
import CamNecT.server.domain.profile.components.majors.model.Majors;
import CamNecT.server.domain.profile.components.majors.repository.MajorRepository;
import CamNecT.server.domain.profile.dto.ProfileGlobalDto;
import CamNecT.server.domain.users.model.UserProfile;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserTagMapRepository;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHomeInboxTest {

    @Mock ChatRequestRepository chatRequestRepository;
    @Mock UserProfileRepository userProfileRepository;
    @Mock UserTagMapRepository userTagMapRepository;
    @Mock MajorRepository majorRepository;
    @Mock PublicUrlIssuer publicUrlIssuer;

    @InjectMocks ChatService chatService;

    @Test
    void separatesCoffeeChatAndRecruitmentCountsAndPreservesSenderPreviews() {
        Long receiverId = 1L;
        Users coffeeSender = Users.builder().userId(2L).name("커피 발신자").build();
        Users recruitmentSender = Users.builder().userId(3L).name("모집 지원자").build();
        ChatRequest coffeeRequest = request(11L, coffeeSender);
        ChatRequest recruitmentRequest = request(12L, recruitmentSender);
        UserProfile recruitmentProfile = UserProfile.builder()
                .userId(3L)
                .user(recruitmentSender)
                .bio("함께 성장할 팀을 찾고 있습니다.")
                .openToCoffeeChat(true)
                .profileImageKey("profile/user-3.png")
                .studentNo("20230002")
                .majorId(7L)
                .build();

        when(chatRequestRepository.countByReceiver_UserIdAndTypeAndStatus(
                receiverId, ChatRequest.RequestType.COFFEE_CHAT, ChatRequest.RequestStatus.WAITING
        )).thenReturn(2L);
        when(chatRequestRepository.countByReceiver_UserIdAndTypeAndStatus(
                receiverId, ChatRequest.RequestType.TEAM_RECRUIT, ChatRequest.RequestStatus.WAITING
        )).thenReturn(3L);
        when(chatRequestRepository.findLatestReceivedRequestsByType(
                receiverId,
                ChatRequest.RequestType.COFFEE_CHAT,
                ChatRequest.RequestStatus.WAITING,
                PageRequest.of(0, 2)
        )).thenReturn(List.of(coffeeRequest));
        when(chatRequestRepository.findLatestReceivedRequestsByType(
                receiverId,
                ChatRequest.RequestType.TEAM_RECRUIT,
                ChatRequest.RequestStatus.WAITING,
                PageRequest.of(0, 5)
        )).thenReturn(List.of(recruitmentRequest));
        when(userProfileRepository.findGlobalsByUserIdIn(List.of(2L)))
                .thenReturn(List.of(new ProfileGlobalDto(2L, "커피 발신자", "컴퓨터공학", "20240001", null)));
        when(userProfileRepository.findAllByUserIdInWithUser(List.of(3L)))
                .thenReturn(List.of(recruitmentProfile));
        when(majorRepository.findAllById(List.of(7L)))
                .thenReturn(List.of(Majors.builder().majorId(7L).majorNameKor("경영학").build()));
        when(userTagMapRepository.findTagNamesWithUserIdByUserIdIn(List.of(3L)))
                .thenReturn(List.of(new Object[]{3L, "백엔드"}, new Object[]{3L, "팀 프로젝트"}));
        when(publicUrlIssuer.issuePublicUrl("profile/user-3.png"))
                .thenReturn("https://cdn.camnect.site/profile/user-3.png");

        HomeResponse.CoffeeChatSection coffeeChat = chatService.getHomeInbox(receiverId, 2);
        HomeResponse.RecruitmentSection recruitment = chatService.getHomeRecruitmentInbox(receiverId, 5);

        assertThat(coffeeChat.pendingCount()).isEqualTo(2L);
        assertThat(coffeeChat.latest2()).singleElement().satisfies(preview -> {
            assertThat(preview.requestId()).isEqualTo(11L);
            assertThat(preview.senderUserId()).isEqualTo(2L);
            assertThat(preview.senderName()).isEqualTo("커피 발신자");
            assertThat(preview.majorName()).isEqualTo("컴퓨터공학");
            assertThat(preview.studentNo()).isEqualTo("20240001");
        });
        assertThat(recruitment.pendingCount()).isEqualTo(3L);
        assertThat(recruitment.latest5()).singleElement().satisfies(preview -> {
            assertThat(preview.requestId()).isEqualTo(12L);
            assertThat(preview.userId()).isEqualTo(3L);
            assertThat(preview.name()).isEqualTo("모집 지원자");
            assertThat(preview.majorName()).isEqualTo("경영학");
            assertThat(preview.profile().bio()).isEqualTo("함께 성장할 팀을 찾고 있습니다.");
            assertThat(preview.profile().openToCoffeeChat()).isTrue();
            assertThat(preview.profile().profileImageUrl())
                    .isEqualTo("https://cdn.camnect.site/profile/user-3.png");
            assertThat(preview.profile().studentNo()).isEqualTo("20230002");
            assertThat(preview.profile().majorId()).isEqualTo(7L);
            assertThat(preview.tagList()).containsExactly("백엔드", "팀 프로젝트");
        });

        JsonNode recruitmentJson = new ObjectMapper().valueToTree(recruitment);
        JsonNode previewJson = recruitmentJson.path("latest5").get(0);
        assertThat(previewJson.has("userId")).isTrue();
        assertThat(previewJson.has("name")).isTrue();
        assertThat(previewJson.has("majorName")).isTrue();
        assertThat(previewJson.has("profile")).isTrue();
        assertThat(previewJson.has("tagList")).isTrue();
        assertThat(previewJson.has("requestId")).isTrue();
        assertThat(previewJson.size()).isEqualTo(6);
        assertThat(previewJson.has("senderUserId")).isFalse();
        assertThat(previewJson.has("senderName")).isFalse();
        assertThat(previewJson.has("studentNo")).isFalse();

        JsonNode profileJson = previewJson.path("profile");
        assertThat(profileJson.has("bio")).isTrue();
        assertThat(profileJson.has("openToCoffeeChat")).isTrue();
        assertThat(profileJson.has("profileImageUrl")).isTrue();
        assertThat(profileJson.has("studentNo")).isTrue();
        assertThat(profileJson.has("majorId")).isTrue();
        assertThat(profileJson.size()).isEqualTo(5);
    }

    @Test
    void returnsEmptySectionsWithoutLoadingPreviewsWhenNoRequestsAreWaiting() {
        Long receiverId = 1L;
        when(chatRequestRepository.countByReceiver_UserIdAndTypeAndStatus(
                receiverId, ChatRequest.RequestType.COFFEE_CHAT, ChatRequest.RequestStatus.WAITING
        )).thenReturn(0L);
        when(chatRequestRepository.countByReceiver_UserIdAndTypeAndStatus(
                receiverId, ChatRequest.RequestType.TEAM_RECRUIT, ChatRequest.RequestStatus.WAITING
        )).thenReturn(0L);

        HomeResponse.CoffeeChatSection coffeeChat = chatService.getHomeInbox(receiverId, 2);
        HomeResponse.RecruitmentSection recruitment = chatService.getHomeRecruitmentInbox(receiverId, 5);

        assertThat(coffeeChat).isEqualTo(HomeResponse.CoffeeChatSection.empty());
        assertThat(recruitment).isEqualTo(HomeResponse.RecruitmentSection.empty());
        verify(chatRequestRepository, never()).findLatestReceivedRequestsByType(
                receiverId,
                ChatRequest.RequestType.COFFEE_CHAT,
                ChatRequest.RequestStatus.WAITING,
                PageRequest.of(0, 2)
        );
        verify(chatRequestRepository, never()).findLatestReceivedRequestsByType(
                receiverId,
                ChatRequest.RequestType.TEAM_RECRUIT,
                ChatRequest.RequestStatus.WAITING,
                PageRequest.of(0, 5)
        );
    }

    @Test
    void preservesPendingCountWithoutProfileQueriesWhenRequestsDisappearDuringLookup() {
        Long receiverId = 1L;
        when(chatRequestRepository.countByReceiver_UserIdAndTypeAndStatus(
                receiverId, ChatRequest.RequestType.TEAM_RECRUIT, ChatRequest.RequestStatus.WAITING
        )).thenReturn(1L);
        when(chatRequestRepository.findLatestReceivedRequestsByType(
                receiverId,
                ChatRequest.RequestType.TEAM_RECRUIT,
                ChatRequest.RequestStatus.WAITING,
                PageRequest.of(0, 5)
        )).thenReturn(List.of());

        HomeResponse.RecruitmentSection recruitment = chatService.getHomeRecruitmentInbox(receiverId, 5);

        assertThat(recruitment.pendingCount()).isEqualTo(1L);
        assertThat(recruitment.latest5()).isEmpty();
        verify(userProfileRepository, never()).findAllByUserIdInWithUser(List.of());
        verify(userTagMapRepository, never()).findTagNamesWithUserIdByUserIdIn(List.of());
    }

    private ChatRequest request(Long requestId, Users requester) {
        ChatRequest request = mock(ChatRequest.class);
        when(request.getId()).thenReturn(requestId);
        when(request.getRequester()).thenReturn(requester);
        return request;
    }
}
