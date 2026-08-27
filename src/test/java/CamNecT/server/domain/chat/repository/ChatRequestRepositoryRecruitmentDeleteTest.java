package CamNecT.server.domain.chat.repository;

import CamNecT.server.domain.chat.model.ChatRequest;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class ChatRequestRepositoryRecruitmentDeleteTest {

    @Autowired TestEntityManager entityManager;
    @Autowired ChatRequestRepository chatRequestRepository;

    @Test
    void deletionQueryLocksOnlyWaitingRequestsAndPreservesProcessedHistory() {
        Users requester = entityManager.persist(user("requester", "requester@example.com"));
        Users receiver = entityManager.persist(user("receiver", "receiver@example.com"));

        ChatRequest waiting = request(requester, receiver, 20L);
        ChatRequest accepted = request(requester, receiver, 20L);
        accepted.accept();
        ChatRequest rejected = request(requester, receiver, 20L);
        rejected.reject();
        entityManager.persist(waiting);
        entityManager.persist(accepted);
        entityManager.persist(rejected);
        entityManager.flush();
        entityManager.clear();

        List<ChatRequest> locked = chatRequestRepository.findAllByRecruitmentIdAndTypeAndStatusForUpdate(
                ChatRequest.RequestType.TEAM_RECRUIT,
                20L,
                ChatRequest.RequestStatus.WAITING
        );
        locked.forEach(ChatRequest::reject);
        entityManager.flush();
        entityManager.clear();

        assertThat(locked).hasSize(1);
        assertThat(chatRequestRepository.findById(waiting.getId()).orElseThrow().getStatus())
                .isEqualTo(ChatRequest.RequestStatus.REJECTED);
        assertThat(chatRequestRepository.findById(accepted.getId()).orElseThrow().getStatus())
                .isEqualTo(ChatRequest.RequestStatus.ACCEPTED);
        assertThat(chatRequestRepository.findById(rejected.getId()).orElseThrow().getStatus())
                .isEqualTo(ChatRequest.RequestStatus.REJECTED);
        assertThat(chatRequestRepository.findAll())
                .allSatisfy(request -> assertThat(request.getRecruitmentId()).isEqualTo(20L));
    }

    private Users user(String username, String email) {
        return Users.builder()
                .username(username)
                .passwordHash("hash")
                .name(username)
                .email(email)
                .build();
    }

    private ChatRequest request(Users requester, Users receiver, Long recruitmentId) {
        ChatRequest request = ChatRequest.builder()
                .requester(requester)
                .receiver(receiver)
                .requestInterest(List.of())
                .content("지원합니다.")
                .type(ChatRequest.RequestType.TEAM_RECRUIT)
                .activityId(10L)
                .recruitmentId(recruitmentId)
                .build();
        ReflectionTestUtils.setField(request, "createdAt", LocalDateTime.now());
        return request;
    }
}
