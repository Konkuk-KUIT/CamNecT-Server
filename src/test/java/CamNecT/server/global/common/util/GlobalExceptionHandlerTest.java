package CamNecT.server.global.common.util;

import CamNecT.server.global.common.response.ValidationErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validationResponseContainsFieldAndMessageWithoutRejectedValue() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError(
                "request", "title", null, false, new String[]{"NotBlank"}, null, "제목은 필수입니다."
        ));
        BindException exception = new BindException(bindingResult);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/community/posts");

        ValidationErrorResponse body = handler.handleValidation(exception, request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo(40000);
        assertThat(body.errors()).containsExactly(
                new ValidationErrorResponse.FieldViolation("title", "제목은 필수입니다.")
        );
    }
}
