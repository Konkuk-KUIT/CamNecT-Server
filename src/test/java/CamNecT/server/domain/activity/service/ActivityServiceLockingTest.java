package CamNecT.server.domain.activity.service;

import CamNecT.server.domain.activity.dto.request.ActivityRequest;
import CamNecT.server.domain.activity.model.enums.ActivityCategory;
import CamNecT.server.domain.activity.model.external_activity.ExternalActivity;
import CamNecT.server.domain.activity.model.external_activity.ExternalActivityBookmark;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityAttachmentRepository;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityBookmarkRepository;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityRepository;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityTagRepository;
import CamNecT.server.domain.activity.repository.recruitment.TeamRecruitmentRepository;
import CamNecT.server.domain.community.service.AuthorAssembler;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.ActivityErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.service.PresignEngine;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import CamNecT.server.global.tag.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceLockingTest {

    @Mock ExternalActivityRepository activityRepository;
    @Mock ExternalActivityTagRepository activityTagRepository;
    @Mock ExternalActivityAttachmentRepository activityAttachmentRepository;
    @Mock ExternalActivityBookmarkRepository activityBookmarkRepository;
    @Mock TagRepository tagRepository;
    @Mock TeamRecruitmentRepository teamRecruitmentRepository;
    @Mock UserRepository userRepository;
    @Mock AccountAccessGuard accountAccessGuard;
    @Mock AuthorAssembler authorAssembler;
    @Mock UploadTicketRepository uploadTicketRepository;
    @Mock PresignEngine presignEngine;
    @Mock PublicUrlIssuer publicUrlIssuer;
    @Mock GlobalPresignMethods globalPresignMethods;

    @InjectMocks ActivityService service;

    @Test
    void creationLocksAccountBeforePersistingAndUsesTheLockedUser() {
        Users owner = Users.builder().userId(1L).build();
        ActivityRequest request = new ActivityRequest(
                ActivityCategory.STUDY,
                "스터디",
                null,
                "본문",
                null,
                null
        );
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(owner);
        when(activityRepository.save(any(ExternalActivity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(1L, request);

        InOrder order = inOrder(accountAccessGuard, activityRepository);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        order.verify(activityRepository).save(any(ExternalActivity.class));
        ArgumentCaptor<ExternalActivity> captor = ArgumentCaptor.forClass(ExternalActivity.class);
        verify(activityRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(owner);
        verify(userRepository, never()).getReferenceById(1L);
    }

    @Test
    void adminCloseLocksAccountBeforeTheActivity() {
        Users admin = Users.builder().userId(9L).role(UserRole.ADMIN).build();
        ExternalActivity activity = ExternalActivity.builder()
                .activityId(10L)
                .user(admin)
                .category(ActivityCategory.EXTERNAL)
                .title("대외활동")
                .build();
        when(accountAccessGuard.requireAccessibleForUpdate(9L)).thenReturn(admin);
        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));

        service.closeActivityAdmin(9L, 10L);

        InOrder order = inOrder(accountAccessGuard, activityRepository);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(9L);
        order.verify(activityRepository).findByIdForUpdate(10L);
    }

    @Test
    void deletionLocksActivityBeforeDeletingFkChildren() {
        Users owner = Users.builder().userId(1L).build();
        ExternalActivity activity = ExternalActivity.builder()
                .activityId(10L)
                .user(owner)
                .category(ActivityCategory.STUDY)
                .title("스터디")
                .build();

        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(owner);
        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));
        when(activityAttachmentRepository.findAllByActivity_ActivityId(10L)).thenReturn(List.of());

        service.delete(10L, 1L);

        InOrder order = inOrder(
                accountAccessGuard,
                activityRepository,
                teamRecruitmentRepository,
                activityAttachmentRepository,
                activityTagRepository,
                activityBookmarkRepository
        );
        order.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        order.verify(activityRepository).findByIdForUpdate(10L);
        order.verify(teamRecruitmentRepository).existsByActivityId(10L);
        order.verify(activityAttachmentRepository).findAllByActivity_ActivityId(10L);
        order.verify(activityTagRepository).deleteAllByActivityId(10L);
        order.verify(activityBookmarkRepository).deleteAllByActivityId(10L);
        order.verify(activityAttachmentRepository).deleteAllByActivityId(10L);
        order.verify(activityRepository).delete(activity);
    }

    @Test
    void activityWithRecruitmentCannotBePhysicallyDeleted() {
        Users owner = Users.builder().userId(1L).build();
        ExternalActivity activity = ExternalActivity.builder()
                .activityId(10L)
                .user(owner)
                .category(ActivityCategory.EXTERNAL)
                .title("모집글 보유 활동")
                .build();
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(owner);
        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));
        when(teamRecruitmentRepository.existsByActivityId(10L)).thenReturn(true);

        CustomException exception = assertThrows(CustomException.class, () -> service.delete(10L, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ActivityErrorCode.ACTIVITY_HAS_RECRUITMENTS);
        verifyNoInteractions(activityTagRepository, activityAttachmentRepository, activityBookmarkRepository);
        verify(activityRepository, never()).delete(any());
    }

    @Test
    void moderationDeletionDoesNotRecheckAccountStateAfterPenaltyApplication() {
        Users owner = Users.builder().userId(9L).role(UserRole.ADMIN).build();
        ExternalActivity activity = ExternalActivity.builder()
                .activityId(10L)
                .user(owner)
                .category(ActivityCategory.EXTERNAL)
                .title("신고 대상")
                .build();
        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));
        when(userRepository.existsByUserIdAndRole(9L, UserRole.ADMIN)).thenReturn(true);
        when(activityAttachmentRepository.findAllByActivity_ActivityId(10L)).thenReturn(List.of());

        service.deleteForModeration(9L, 10L);

        verifyNoInteractions(accountAccessGuard);
        verify(activityRepository).delete(activity);
    }

    @Test
    void bookmarkMutationUsesTheLockedActivityInstance() {
        Users owner = Users.builder().userId(1L).build();
        Users user = Users.builder().userId(2L).build();
        ExternalActivity activity = ExternalActivity.builder()
                .activityId(10L)
                .user(owner)
                .category(ActivityCategory.STUDY)
                .title("스터디")
                .build();

        when(accountAccessGuard.requireAccessibleForUpdate(2L)).thenReturn(user);
        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));
        when(activityBookmarkRepository.findByUser_UserIdAndActivity_ActivityId(2L, 10L))
                .thenReturn(Optional.empty());

        assertThat(service.toggleActivityBookmark(2L, 10L)).isTrue();

        ArgumentCaptor<ExternalActivityBookmark> captor = ArgumentCaptor.forClass(ExternalActivityBookmark.class);
        verify(activityBookmarkRepository).save(captor.capture());
        assertThat(captor.getValue().getActivity()).isSameAs(activity);
        verify(activityRepository, never()).existsById(10L);
        verify(activityRepository, never()).getReferenceById(10L);
        verify(userRepository, never()).getReferenceById(2L);
    }

    @Test
    void attachmentAndTagUpdateStartsWithActivityWriteLock() {
        Users owner = Users.builder().userId(1L).build();
        ExternalActivity activity = ExternalActivity.builder()
                .activityId(10L)
                .user(owner)
                .category(ActivityCategory.STUDY)
                .title("기존 제목")
                .build();
        ActivityRequest request = new ActivityRequest(
                ActivityCategory.STUDY,
                "변경 제목",
                null,
                "변경 본문",
                null,
                null
        );
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(owner);
        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));

        service.update(1L, 10L, request);

        InOrder order = inOrder(accountAccessGuard, activityRepository);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        order.verify(activityRepository).findByIdForUpdate(10L);
        verify(activityRepository, never()).findById(10L);
        assertThat(activity.getTitle()).isEqualTo("변경 제목");
    }

    @Test
    void closingLocksAccountBeforeTheActivity() {
        Users owner = Users.builder().userId(1L).build();
        ExternalActivity activity = ExternalActivity.builder()
                .activityId(10L)
                .user(owner)
                .category(ActivityCategory.STUDY)
                .title("스터디")
                .build();
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(owner);
        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));

        service.closeActivity(1L, 10L);

        InOrder order = inOrder(accountAccessGuard, activityRepository);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        order.verify(activityRepository).findByIdForUpdate(10L);
    }

    @Test
    void withdrawnAccountIsRejectedBeforeAnyActivityMutation() {
        when(accountAccessGuard.requireAccessibleForUpdate(1L))
                .thenThrow(new CustomException(AuthErrorCode.USER_WITHDRAWN));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.delete(10L, 1L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_WITHDRAWN);
        verifyNoInteractions(activityRepository, activityTagRepository, activityAttachmentRepository,
                activityBookmarkRepository, teamRecruitmentRepository, globalPresignMethods);
    }

}
