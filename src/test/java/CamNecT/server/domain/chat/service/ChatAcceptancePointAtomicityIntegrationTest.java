package CamNecT.server.domain.chat.service;

import CamNecT.server.domain.chat.model.ChatRequest;
import CamNecT.server.domain.chat.repository.ChatRequestRepository;
import CamNecT.server.domain.chat.repository.ChatRoomRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.point.service.PointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatAcceptancePointAtomicityIntegrationTest {

    @Autowired ChatService chatService;
    @Autowired UserRepository userRepository;
    @Autowired ChatRequestRepository chatRequestRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean PointService pointService;

    @Test
    void pointRewardFailureRollsBackTheAcceptedRequestAndNewRoom() {
        Fixture fixture = createFixture();
        IllegalStateException pointFailure = new IllegalStateException("point persistence failed");
        doThrow(pointFailure).when(pointService)
                .earnPoint(eq(fixture.requesterId()), anyInt(), any());

        assertThatThrownBy(() -> chatService.respondToRequest(
                fixture.requestId(), fixture.receiverId(), true))
                .isSameAs(pointFailure);

        ChatRequest persisted = chatRequestRepository.findById(fixture.requestId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ChatRequest.RequestStatus.WAITING);
        assertThat(chatRoomRepository.findByRequest_Id(fixture.requestId())).isEmpty();
    }

    private Fixture createFixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Users requester = userRepository.save(Users.builder()
                    .username("atomic-requester-" + suffix)
                    .passwordHash("password")
                    .name("requester")
                    .status(UserStatus.ACTIVE)
                    .build());
            Users receiver = userRepository.save(Users.builder()
                    .username("atomic-receiver-" + suffix)
                    .passwordHash("password")
                    .name("receiver")
                    .status(UserStatus.ACTIVE)
                    .build());
            ChatRequest request = chatRequestRepository.save(ChatRequest.builder()
                    .requester(requester)
                    .receiver(receiver)
                    .requestInterest(List.of())
                    .content("request")
                    .type(ChatRequest.RequestType.COFFEE_CHAT)
                    .build());
            return new Fixture(request.getId(), requester.getUserId(), receiver.getUserId());
        });
    }

    private record Fixture(Long requestId, Long requesterId, Long receiverId) {}
}
