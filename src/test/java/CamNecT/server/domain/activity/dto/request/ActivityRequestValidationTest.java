package CamNecT.server.domain.activity.dto.request;

import CamNecT.server.domain.activity.model.enums.ActivityCategory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void categoryAndTitleAreRequiredWhileOtherFieldsRemainOptional() {
        ActivityRequest invalid = new ActivityRequest(null, " ", null, null, null, null);
        ActivityRequest valid = new ActivityRequest(ActivityCategory.STUDY, "제목", null, null, null, null);

        assertThat(validator.validate(invalid))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("category", "title");
        assertThat(validator.validate(valid)).isEmpty();
    }

    @Test
    void titleLengthAndTagIdsMatchPersistenceContract() {
        ActivityRequest invalid = new ActivityRequest(
                ActivityCategory.STUDY,
                "x".repeat(201),
                List.of(1L, -1L),
                null,
                null,
                null
        );

        assertThat(validator.validate(invalid))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("title", "tagIds[1].<list element>");
    }
}
