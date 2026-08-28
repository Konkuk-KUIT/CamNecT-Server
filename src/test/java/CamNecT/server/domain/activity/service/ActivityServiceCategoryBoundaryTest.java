package CamNecT.server.domain.activity.service;

import CamNecT.server.domain.activity.dto.request.ActivityRequest;
import CamNecT.server.domain.activity.model.enums.ActivityCategory;
import CamNecT.server.domain.activity.model.external_activity.ExternalActivity;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityAttachmentRepository;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityBookmarkRepository;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityRepository;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityTagRepository;
import CamNecT.server.domain.activity.repository.recruitment.TeamRecruitmentRepository;
import CamNecT.server.domain.community.service.AuthorAssembler;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.ActivityErrorCode;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.service.PresignEngine;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import CamNecT.server.global.tag.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceCategoryBoundaryTest {

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

    @ParameterizedTest
    @EnumSource(value = ActivityCategory.class, names = {"EXTERNAL", "RECRUITMENT"})
    void createRejectsAdminOnlyCategoriesBeforePersistence(ActivityCategory category) {
        ActivityRequest request = request(category);

        CustomException exception = assertThrows(CustomException.class, () -> service.create(1L, request));

        assertThat(exception.getErrorCode()).isEqualTo(ActivityErrorCode.INVALID_ACTIVITY_CATEGORY);
        verifyNoInteractions(userRepository, activityRepository);
    }

    @ParameterizedTest
    @EnumSource(value = ActivityCategory.class, names = {"EXTERNAL", "RECRUITMENT"})
    void updateRejectsAdminOnlyRequestedCategoriesBeforeLoadingActivity(ActivityCategory category) {
        ActivityRequest request = request(category);

        CustomException exception = assertThrows(CustomException.class, () -> service.update(1L, 10L, request));

        assertThat(exception.getErrorCode()).isEqualTo(ActivityErrorCode.INVALID_ACTIVITY_CATEGORY);
        verifyNoInteractions(activityRepository);
    }

    @ParameterizedTest
    @EnumSource(value = ActivityCategory.class, names = {"EXTERNAL", "RECRUITMENT"})
    void updateRejectsAdminOnlyExistingActivitiesEvenWhenRequestedCategoryIsGeneral(ActivityCategory existingCategory) {
        Users owner = Users.builder().userId(1L).build();
        ExternalActivity activity = activity(existingCategory, owner);
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(owner);
        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.update(1L, 10L, request(ActivityCategory.STUDY))
        );

        assertThat(exception.getErrorCode()).isEqualTo(ActivityErrorCode.INVALID_ACTIVITY_CATEGORY);
        verify(activityRepository).findByIdForUpdate(10L);
        verify(activityRepository, never()).save(any());
        verifyNoInteractions(activityAttachmentRepository, activityTagRepository, tagRepository, presignEngine);
        assertThat(activity.getCategory()).isEqualTo(existingCategory);
    }

    @Test
    void updateAllowsChangingBetweenGeneralCategories() {
        Users owner = Users.builder().userId(1L).build();
        ExternalActivity activity = activity(ActivityCategory.STUDY, owner);
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(owner);
        when(activityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));

        service.update(1L, 10L, request(ActivityCategory.CLUB));

        assertThat(activity.getCategory()).isEqualTo(ActivityCategory.CLUB);
    }

    @Test
    void createRejectsUnknownOrInactiveTagsBeforePersistence() {
        Users owner = Users.builder().userId(1L).build();
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(owner);
        when(tagRepository.findExistingActiveIds(List.of(10L, 20L))).thenReturn(List.of(10L));
        ActivityRequest request = new ActivityRequest(
                ActivityCategory.STUDY,
                "제목",
                List.of(10L, 20L),
                "본문",
                null,
                null
        );

        CustomException exception = assertThrows(CustomException.class, () -> service.create(1L, request));

        assertThat(exception.getErrorCode()).isEqualTo(ActivityErrorCode.INVALID_TAG_IDS);
        verifyNoInteractions(userRepository, activityRepository, presignEngine);
        verify(accountAccessGuard).requireAccessibleForUpdate(1L);
    }

    private ActivityRequest request(ActivityCategory category) {
        return new ActivityRequest(category, "제목", null, "본문", null, null);
    }

    private ExternalActivity activity(ActivityCategory category, Users owner) {
        return ExternalActivity.builder()
                .activityId(10L)
                .user(owner)
                .category(category)
                .title("기존 제목")
                .build();
    }
}
