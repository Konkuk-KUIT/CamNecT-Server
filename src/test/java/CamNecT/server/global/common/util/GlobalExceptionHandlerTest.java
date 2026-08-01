package CamNecT.server.global.common.util;

import CamNecT.server.global.common.response.ErrorResponse;
import CamNecT.server.global.common.response.ValidationErrorResponse;
import io.lettuce.core.RedisCommandTimeoutException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
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

    @Test
    void redisTimeoutReturnsServiceUnavailableWithoutExposingInfrastructureDetails() {
        QueryTimeoutException exception = new QueryTimeoutException(
                "Redis command timed out",
                new RedisCommandTimeoutException("Command timed out")
        );
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/auth/login");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("3");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(50310);
        assertThat(response.getBody().message()).doesNotContainIgnoringCase("redis");
    }
}
