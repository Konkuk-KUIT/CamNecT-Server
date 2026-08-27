package CamNecT.server.domain.activity.repository.external_activity;

import CamNecT.server.domain.activity.model.enums.ActivityCategory;
import CamNecT.server.domain.activity.model.external_activity.ExternalActivity;
import CamNecT.server.domain.activity.model.external_activity.ExternalActivityAttachment;
import CamNecT.server.domain.activity.model.external_activity.ExternalActivityBookmark;
import CamNecT.server.domain.activity.model.external_activity.ExternalActivityTag;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.config.QuerydslConfig;
import CamNecT.server.global.tag.model.Tag;
import CamNecT.server.global.tag.model.TagCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class ExternalActivityRepositoryDeleteTest {

    @Autowired TestEntityManager entityManager;
    @Autowired ExternalActivityRepository activityRepository;
    @Autowired ExternalActivityTagRepository tagRepository;
    @Autowired ExternalActivityAttachmentRepository attachmentRepository;
    @Autowired ExternalActivityBookmarkRepository bookmarkRepository;

    @Test
    void lockedActivityCanBeDeletedAfterAllFkChildren() {
        Users owner = entityManager.persist(Users.builder()
                .username("activity-delete-owner")
                .passwordHash("hash")
                .name("작성자")
                .email("activity-delete@example.com")
                .build());
        TagCategory category = entityManager.persist(TagCategory.builder()
                .code("activity-delete-test")
                .name("삭제 테스트")
                .build());
        Tag tag = entityManager.persist(Tag.builder()
                .name("삭제태그")
                .category(category)
                .build());
        ExternalActivity activity = entityManager.persist(ExternalActivity.builder()
                .user(owner)
                .title("삭제 대상")
                .category(ActivityCategory.EXTERNAL)
                .build());

        entityManager.persist(ExternalActivityTag.builder().activity(activity).tag(tag).build());
        entityManager.persist(ExternalActivityAttachment.builder()
                .activity(activity)
                .fileKey("activity/delete/file.pdf")
                .build());
        entityManager.persist(ExternalActivityBookmark.of(owner, activity));
        entityManager.flush();
        entityManager.clear();

        ExternalActivity locked = activityRepository.findByIdForUpdate(activity.getActivityId()).orElseThrow();
        tagRepository.deleteAllByActivityId(activity.getActivityId());
        bookmarkRepository.deleteAllByActivityId(activity.getActivityId());
        attachmentRepository.deleteAllByActivityId(activity.getActivityId());
        activityRepository.delete(locked);
        entityManager.flush();

        assertThat(activityRepository.findById(activity.getActivityId())).isEmpty();
        assertThat(tagRepository.findAll()).isEmpty();
        assertThat(bookmarkRepository.findAll()).isEmpty();
        assertThat(attachmentRepository.findAll()).isEmpty();
    }
}
