package CamNecT.server.domain.chat.dto.request.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestAcceptDtoValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void missingDecisionIsRejectedInsteadOfBecomingFalse() throws Exception {
        ChatRequestAcceptDto request = objectMapper.readValue(
                "{\"requestId\":1}", ChatRequestAcceptDto.class);

        assertThat(request.isAccepted()).isNull();
        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("isAccepted"));
    }
}
