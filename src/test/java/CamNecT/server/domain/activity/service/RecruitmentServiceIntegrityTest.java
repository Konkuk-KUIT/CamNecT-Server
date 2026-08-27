package CamNecT.server.domain.activity.service;

import CamNecT.server.domain.activity.dto.request.RecruitmentRequest;
import CamNecT.server.domain.activity.model.enums.ActivityCategory;
import CamNecT.server.domain.activity.model.external_activity.ExternalActivity;
import CamNecT.server.domain.activity.model.recruitment.TeamRecruitment;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityRepository;
import CamNecT.server.domain.activity.repository.recruitment.RecruitmentBookmarkRepository;
import CamNecT.server.domain.activity.repository.recruitment.TeamApplicationRepository;
import CamNecT.server.domain.activity.repository.recruitment.TeamRecruitmentRepository;
import CamNecT.server.domain.chat.model.ChatRequest;
import CamNecT.server.domain.chat.repository.ChatRequestRepository;
import CamNecT.server.domain.community.service.AuthorAssembler;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecruitmentServiceIntegrityTest {

    @Mock TeamRecruitmentRepository recruitmentRepository;
    @Mock ExternalActivityRepository activityRepository;
    @Mock RecruitmentBookmarkRepository bookmarkRepository;
    @Mock TeamApplicationRepository teamApplicationRepository;
    @Mock UserRepository userRepository;
    @Mock ChatRequestRepository chatRequestRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AuthorAssembler authorAssembler;

    @InjectMocks RecruitmentService service;

    @Test
    void creationLocksActivitySoPhysicalDeletionCannotPassTheExistenceCheck() {
        Users author = activeUser(1L);
        ExternalActivity activity = ExternalActivity.builder()
                .activityId(10L)
                .category(ActivityCategory.EXTERNAL)
                .title("활동")
                .build();
        RecruitmentRequest request = new RecruitmentRequest(
                10L,
                "모집글",
                LocalDate.of(2026, 12, 31),
                2,
                "함께할 팀원을 모집합니다."
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));
        when(recruitmentRepository.save(any(TeamRecruitment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TeamRecruitment saved = service.createRecruitment(1L, request);

        verify(activityRepository).findByIdForUpdate(10L);
        verify(activityRepository, never()).findById(10L);
        assertThat(saved.getActivityId()).isEqualTo(10L);
    }

    @Test
    void updateLocksRecruitmentSoItCannotRaceWithPhysicalDeletion() {
        Users author = activeUser(1L);
        TeamRecruitment recruitment = TeamRecruitment.builder()
                .recruitId(20L)
                .activityId(10L)
                .userId(1L)
                .title("기존 제목")
                .content("기존 본문")
                .recruitDeadline(LocalDate.of(2026, 12, 31))
                .build();
        RecruitmentRequest request = new RecruitmentRequest(
                10L,
                "수정 제목",
                LocalDate.of(2027, 1, 31),
                3,
                "수정 본문"
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(recruitmentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(recruitment));

        service.updateRecruitment(1L, 20L, request);

        verify(recruitmentRepository).findByIdForUpdate(20L);
        verify(recruitmentRepository, never()).findById(20L);
        assertThat(recruitment.getTitle()).isEqualTo("수정 제목");
    }

    @Test
    void deletionLocksRecruitmentRejectsWaitingAndThenDeletesDependents() {
        Users author = activeUser(1L);
        TeamRecruitment recruitment = TeamRecruitment.builder()
                .recruitId(20L)
                .activityId(10L)
                .userId(1L)
                .title("모집글")
                .content("본문")
                .recruitDeadline(LocalDate.of(2026, 12, 31))
                .build();
        ChatRequest waiting = mock(ChatRequest.class);

        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(recruitmentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(recruitment));
        when(userRepository.existsByUserIdAndRole(1L, UserRole.ADMIN)).thenReturn(false);
        when(chatRequestRepository.findAllByRecruitmentIdAndTypeAndStatusForUpdate(
                ChatRequest.RequestType.TEAM_RECRUIT,
                20L,
                ChatRequest.RequestStatus.WAITING
        )).thenReturn(List.of(waiting));

        service.deleteRecruitment(1L, 20L);

        InOrder order = inOrder(
                recruitmentRepository,
                chatRequestRepository,
                waiting,
                teamApplicationRepository,
                bookmarkRepository
        );
        order.verify(recruitmentRepository).findByIdForUpdate(20L);
        order.verify(chatRequestRepository).findAllByRecruitmentIdAndTypeAndStatusForUpdate(
                ChatRequest.RequestType.TEAM_RECRUIT,
                20L,
                ChatRequest.RequestStatus.WAITING
        );
        order.verify(waiting).reject();
        order.verify(teamApplicationRepository).deleteByRecruitId(20L);
        order.verify(bookmarkRepository).deleteByRecruitId(20L);
        order.verify(recruitmentRepository).delete(recruitment);
    }

    private Users activeUser(Long userId) {
        return Users.builder().userId(userId).status(UserStatus.ACTIVE).build();
    }
}
