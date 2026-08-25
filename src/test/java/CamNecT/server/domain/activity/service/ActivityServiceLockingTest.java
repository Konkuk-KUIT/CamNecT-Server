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
    @Mock AuthorAssembler authorAssembler;
    @Mock UploadTicketRepository uploadTicketRepository;
    @Mock PresignEngine presignEngine;
    @Mock PublicUrlIssuer publicUrlIssuer;
    @Mock GlobalPresignMethods globalPresignMethods;

    @InjectMocks ActivityService service;

    @Test
    void deletionLocksActivityBeforeDeletingFkChildren() {
        Users owner = Users.builder().userId(1L).build();
        ExternalActivity activity = ExternalActivity.builder()
                .activityId(10L)
                .user(owner)
                .category(ActivityCategory.STUDY)
                .title("스터디")
                .build();

        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));
        when(userRepository.existsByUserIdAndRole(1L, UserRole.ADMIN)).thenReturn(false);
        when(activityAttachmentRepository.findAllByActivity_ActivityId(10L)).thenReturn(List.of());

        service.delete(10L, 1L);

        InOrder order = inOrder(
                activityRepository,
                activityAttachmentRepository,
                activityTagRepository,
                activityBookmarkRepository
        );
        order.verify(activityRepository).findByIdForUpdate(10L);
        order.verify(activityAttachmentRepository).findAllByActivity_ActivityId(10L);
        order.verify(activityTagRepository).deleteAllByActivityId(10L);
        order.verify(activityBookmarkRepository).deleteAllByActivityId(10L);
        order.verify(activityAttachmentRepository).deleteAllByActivityId(10L);
        order.verify(activityRepository).delete(activity);
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

        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));
        when(activityBookmarkRepository.findByUser_UserIdAndActivity_ActivityId(2L, 10L))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(2L)).thenReturn(user);

        assertThat(service.toggleActivityBookmark(2L, 10L)).isTrue();

        ArgumentCaptor<ExternalActivityBookmark> captor = ArgumentCaptor.forClass(ExternalActivityBookmark.class);
        verify(activityBookmarkRepository).save(captor.capture());
        assertThat(captor.getValue().getActivity()).isSameAs(activity);
        verify(activityRepository, never()).existsById(10L);
        verify(activityRepository, never()).getReferenceById(10L);
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
        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));

        service.update(1L, 10L, request);

        verify(activityRepository).findByIdForUpdate(10L);
        verify(activityRepository, never()).findById(10L);
        assertThat(activity.getTitle()).isEqualTo("변경 제목");
    }
}
