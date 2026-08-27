package CamNecT.server.domain.community.dto.request;

import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.global.storage.dto.request.PresignUploadBatchRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createPostRejectsDuplicateOrTooManyTags() {
        CreatePostRequest duplicate = new CreatePostRequest(
                BoardCode.INFO, "제목", "본문", false, List.of(1L, 1L), null
        );
        CreatePostRequest tooMany = new CreatePostRequest(
                BoardCode.INFO, "제목", "본문", false, List.of(1L, 2L, 3L, 4L, 5L, 6L), null
        );

        assertThat(validator.validate(duplicate)).anyMatch(v -> v.getPropertyPath().toString().equals("tagIds"));
        assertThat(validator.validate(tooMany)).anyMatch(v -> v.getPropertyPath().toString().equals("tagIds"));
    }

    @Test
    void updatePostRejectsBlankValues() {
        UpdatePostRequest empty = new UpdatePostRequest(null, null, null, null, null);
        UpdatePostRequest blank = new UpdatePostRequest("   ", "\n\t", null, null, null);

        assertThat(validator.validate(empty)).isEmpty();
        assertThat(validator.validate(blank)).extracting(v -> v.getPropertyPath().toString())
                .contains("title", "content");
    }

    @Test
    void commentsEnforceLengthAndPositiveParentId() {
        CreateCommentRequest request = new CreateCommentRequest(
                "x".repeat(CommunityRequestLimits.MAX_COMMENT_CONTENT_LENGTH + 1),
                0L
        );

        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .contains("content", "parentCommentId");
    }

    @Test
    void attachmentDimensionsMustBePositivePair() {
        AttachmentRequest oneSided = new AttachmentRequest("temp/file.png", 100, null, 10L);
        AttachmentRequest negative = new AttachmentRequest("temp/file.png", -1, -1, -10L);

        assertThat(validator.validate(oneSided)).anyMatch(v -> v.getPropertyPath().toString().equals("dimensionPairValid"));
        assertThat(validator.validate(negative)).extracting(v -> v.getPropertyPath().toString())
                .contains("width", "height", "fileSize");
    }

    @Test
    void uploadItemRejectsUnsafeFilenameAndInvalidMetadata() {
        PresignUploadBatchRequest request = new PresignUploadBatchRequest(List.of(
                new PresignUploadBatchRequest.Item("", 0, "../bad\r\n.pdf")
        ));

        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .contains(
                        "items[0].contentType",
                        "items[0].size",
                        "items[0].originalFilename"
                );
    }
}
