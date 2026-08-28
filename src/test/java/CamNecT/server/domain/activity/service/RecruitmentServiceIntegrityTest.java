package CamNecT.server.domain.activity.service;

import CamNecT.server.domain.activity.dto.request.RecruitmentRequest;
import CamNecT.server.domain.activity.dto.request.RecruitmentApplyRequest;
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
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.ActivityErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecruitmentServiceIntegrityTest {

    @Mock TeamRecruitmentRepository recruitmentRepository;
    @Mock ExternalActivityRepository activityRepository;
    @Mock RecruitmentBookmarkRepository bookmarkRepository;
    @Mock TeamApplicationRepository teamApplicationRepository;
    @Mock UserRepository userRepository;
    @Mock ChatRequestRepository chatRequestRepository;
    @Mock AccountAccessGuard accountAccessGuard;
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
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(author);
        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));
        when(recruitmentRepository.save(any(TeamRecruitment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TeamRecruitment saved = service.createRecruitment(1L, request);

        InOrder order = inOrder(accountAccessGuard, activityRepository);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        order.verify(activityRepository).findByIdForUpdate(10L);
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
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(author);
        when(recruitmentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(recruitment));

        service.updateRecruitment(1L, 20L, request);

        InOrder order = inOrder(accountAccessGuard, recruitmentRepository);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        order.verify(recruitmentRepository).findByIdForUpdate(20L);
        verify(recruitmentRepository, never()).findById(20L);
        assertThat(recruitment.getTitle()).isEqualTo("수정 제목");
    }

    @Test
    void bookmarkLocksAccountBeforeRecruitmentWithoutReversingTheOrder() {
        Users user = activeUser(2L);
        TeamRecruitment recruitment = TeamRecruitment.builder()
                .recruitId(20L)
                .activityId(10L)
                .userId(1L)
                .title("모집글")
                .content("본문")
                .recruitDeadline(LocalDate.of(2026, 12, 31))
                .build();
        when(accountAccessGuard.requireAccessibleForUpdate(2L)).thenReturn(user);
        when(recruitmentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(recruitment));
        when(bookmarkRepository.findByUserIdAndRecruitId(2L, 20L)).thenReturn(Optional.empty());

        assertThat(service.toggleRecruitmentBookmark(2L, 20L)).isTrue();

        InOrder order = inOrder(accountAccessGuard, recruitmentRepository, bookmarkRepository);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(2L);
        order.verify(recruitmentRepository).findByIdForUpdate(20L);
        order.verify(bookmarkRepository).findByUserIdAndRecruitId(2L, 20L);
        verify(userRepository, never()).lockUserRow(2L);
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

        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(author);
        when(recruitmentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(recruitment));
        when(chatRequestRepository.findAllByRecruitmentIdAndTypeAndStatusForUpdate(
                ChatRequest.RequestType.TEAM_RECRUIT,
                20L,
                ChatRequest.RequestStatus.WAITING
        )).thenReturn(List.of(waiting));

        service.deleteRecruitment(1L, 20L);

        InOrder order = inOrder(
                accountAccessGuard,
                recruitmentRepository,
                chatRequestRepository,
                waiting,
                teamApplicationRepository,
                bookmarkRepository
        );
        order.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
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

    @Test
    void adminDeletionUsesTheLockedLatestRoleBeforeRecruitment() {
        Users admin = Users.builder()
                .userId(9L)
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        TeamRecruitment recruitment = TeamRecruitment.builder()
                .recruitId(20L)
                .activityId(10L)
                .userId(1L)
                .title("모집글")
                .content("본문")
                .recruitDeadline(LocalDate.of(2026, 12, 31))
                .build();
        when(accountAccessGuard.requireAccessibleForUpdate(9L)).thenReturn(admin);
        when(recruitmentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(recruitment));
        when(chatRequestRepository.findAllByRecruitmentIdAndTypeAndStatusForUpdate(
                ChatRequest.RequestType.TEAM_RECRUIT,
                20L,
                ChatRequest.RequestStatus.WAITING
        )).thenReturn(List.of());

        service.deleteRecruitment(9L, 20L);

        InOrder order = inOrder(accountAccessGuard, recruitmentRepository);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(9L);
        order.verify(recruitmentRepository).findByIdForUpdate(20L);
        verify(userRepository, never()).existsByUserIdAndRole(9L, UserRole.ADMIN);
        verify(recruitmentRepository).delete(recruitment);
    }

    @Test
    void suspendedAccountIsRejectedBeforeRecruitmentOrApplicationMutation() {
        doThrow(new CustomException(AuthErrorCode.USER_SUSPENDED))
                .when(accountAccessGuard).requireAccessibleSnapshot(2L);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.applyToTeam(2L, 20L, null)
        );

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_SUSPENDED);
        verify(accountAccessGuard).requireAccessibleSnapshot(2L);
        verifyNoInteractions(recruitmentRepository, userRepository, teamApplicationRepository,
                bookmarkRepository, chatRequestRepository, eventPublisher);
    }

    @Test
    void accountStateIsRevalidatedAfterBothUserRowsAreLocked() {
        Users receiver = activeUser(1L);
        when(recruitmentRepository.findUserIdByRecruitId(20L)).thenReturn(Optional.of(1L));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(receiver));
        when(accountAccessGuard.requireAccessibleForUpdate(2L))
                .thenThrow(new CustomException(AuthErrorCode.USER_SUSPENDED));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.applyToTeam(2L, 20L, null)
        );

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_SUSPENDED);
        verify(accountAccessGuard).requireAccessibleSnapshot(2L);
        verify(recruitmentRepository).findUserIdByRecruitId(20L);
        verify(userRepository).findByIdForUpdate(1L);
        verify(recruitmentRepository, never()).findByIdForUpdate(20L);
        verifyNoInteractions(teamApplicationRepository, bookmarkRepository,
                chatRequestRepository, eventPublisher);
    }

    @Test
    void unauthenticatedApplicationPreservesInvalidTokenBeforeResourceLookup() {
        doThrow(new CustomException(AuthErrorCode.INVALID_TOKEN))
                .when(accountAccessGuard).requireAccessibleSnapshot(null);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.applyToTeam(null, 20L, null)
        );

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
        verify(accountAccessGuard).requireAccessibleSnapshot(null);
        verifyNoInteractions(recruitmentRepository, userRepository);
    }

    @Test
    void applicationLocksBothUsersInIdOrderBeforeRecruitment() {
        Users requester = activeUser(3L);
        TeamRecruitment recruitment = TeamRecruitment.builder()
                .recruitId(20L)
                .activityId(10L)
                .userId(1L)
                .title("모집글")
                .content("본문")
                .recruitDeadline(LocalDate.of(2026, 12, 31))
                .build();
        when(recruitmentRepository.findUserIdByRecruitId(20L)).thenReturn(Optional.of(1L));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(activeUser(1L)));
        when(accountAccessGuard.requireAccessibleForUpdate(3L)).thenReturn(requester);
        when(recruitmentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(recruitment));
        when(teamApplicationRepository.existsByRecruitIdAndUserId(20L, 3L)).thenReturn(true);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.applyToTeam(3L, 20L, null)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ActivityErrorCode.ALREADY_APPLIED);
        InOrder order = inOrder(recruitmentRepository, userRepository, accountAccessGuard);
        order.verify(accountAccessGuard).requireAccessibleSnapshot(3L);
        order.verify(recruitmentRepository).findUserIdByRecruitId(20L);
        order.verify(userRepository).findByIdForUpdate(1L);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(3L);
        order.verify(recruitmentRepository).findByIdForUpdate(20L);
    }

    @Test
    void withdrawnRecruitmentAuthorCannotReceiveNewApplications() {
        Users requester = activeUser(2L);
        Users withdrawnReceiver = Users.builder()
                .userId(1L)
                .status(UserStatus.WITHDRAWN)
                .build();
        when(recruitmentRepository.findUserIdByRecruitId(20L)).thenReturn(Optional.of(1L));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(withdrawnReceiver));
        when(accountAccessGuard.requireAccessibleForUpdate(2L)).thenReturn(requester);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.applyToTeam(2L, 20L, null)
        );

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_NOT_FOUND);
        verify(recruitmentRepository, never()).findByIdForUpdate(20L);
        verifyNoInteractions(teamApplicationRepository, chatRequestRepository, eventPublisher);
    }

    @Test
    void applicationUsesReadCommittedAfterTheRecruitmentOwnerScalarRead() throws Exception {
        Transactional transactional = RecruitmentService.class
                .getMethod("applyToTeam", Long.class, Long.class, RecruitmentApplyRequest.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    private Users activeUser(Long userId) {
        return Users.builder().userId(userId).status(UserStatus.ACTIVE).build();
    }
}
