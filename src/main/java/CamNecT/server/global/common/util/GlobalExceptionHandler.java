package CamNecT.server.global.common.util;

import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.exception.InvalidPropertiesException;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import CamNecT.server.global.common.response.errorcode.BaseErrorCode;
import CamNecT.server.global.common.response.ErrorResponse;
import CamNecT.server.global.common.response.InvalidPropertiesErrorResponse;
import CamNecT.server.global.common.response.ValidationErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustom(CustomException e, HttpServletRequest req) {
        BaseErrorCode errorCode = e.getErrorCode();

        log.warn("[CustomException] {} {} | status={} code={}",
                req.getMethod(),
                req.getRequestURI(),
                errorCode.getHttpStatus().value(),
                errorCode.getCode());

        return response(errorCode);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ValidationErrorResponse> handleValidation(Exception e, HttpServletRequest req) {
        List<ValidationErrorResponse.FieldViolation> errors = extractValidationErrors(e);
        if (errors.isEmpty()) {
            errors = List.of(new ValidationErrorResponse.FieldViolation(
                    "$request", "요청값이 올바르지 않습니다."
            ));
        }

        log.warn("[ValidationError] {} {} | code={} | exception={} | fields={} | ua={}",
                req.getMethod(),
                req.getRequestURI(),
                ErrorCode.BAD_REQUEST.getCode(),
                e.getClass().getSimpleName(),
                errors.stream().map(ValidationErrorResponse.FieldViolation::field).toList(),
                req.getHeader("User-Agent")
        );

        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(new ValidationErrorResponse(
                        ErrorCode.BAD_REQUEST.getHttpStatus().value(),
                        ErrorCode.BAD_REQUEST.getCode(),
                        ErrorCode.BAD_REQUEST.getMessage(),
                        errors
                ));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingPathVariableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest req) {
        log.warn("[BadRequest] {} {} | code={} | exception={} | ua={}",
                req.getMethod(),
                req.getRequestURI(),
                ErrorCode.BAD_REQUEST.getCode(),
                e.getClass().getSimpleName(),
                req.getHeader("User-Agent")
        );

        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(new ErrorResponse(
                        ErrorCode.BAD_REQUEST.getHttpStatus().value(),
                        ErrorCode.BAD_REQUEST.getCode(),
                        ErrorCode.BAD_REQUEST.getMessage()
                ));
    }

    @ExceptionHandler(InvalidPropertiesException.class)
    public ResponseEntity<InvalidPropertiesErrorResponse> handleInvalidProperties(
            InvalidPropertiesException e,
            HttpServletRequest req
    ) {
        log.warn("[InvalidPropertiesException] {} {} | status={} code={} | invalidProperties={} | ua={}",
                req.getMethod(),
                req.getRequestURI(),
                e.getHttpStatus().value(),
                e.getCode(),
                e.getInvalidProperties(),
                req.getHeader("User-Agent")
        );

        return ResponseEntity.status(e.getHttpStatus())
                .body(new InvalidPropertiesErrorResponse(
                        e.getHttpStatus().value(),
                        e.getCode(),
                        e.getMessage(),
                        e.getInvalidProperties()
                ));
    }


//    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
//    public ResponseEntity<ErrorResponse> handleUnreadableBody(Exception e, HttpServletRequest req) {
//        log.warn("[UnreadableBody] {} {}", req.getMethod(), req.getRequestURI());
//        return response(ErrorCode.BAD_REQUEST);
//    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(Exception e) {
        return response(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(Exception e) {
        return response(ErrorCode.NOT_ACCEPTABLE);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(Exception e) {
        return response(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handlePayloadTooLarge(Exception e) {
        return response(ErrorCode.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
        return response(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest req) {
        log.error("[UnexpectedException] {} {}", req.getMethod(), req.getRequestURI(), e);
        return response(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> response(BaseErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(new ErrorResponse(
                        errorCode.getHttpStatus().value(),
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }

    private List<ValidationErrorResponse.FieldViolation> extractValidationErrors(Exception e) {
        if (e instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            return fromBindingResult(methodArgumentNotValidException.getBindingResult());
        }
        if (e instanceof BindException bindException) {
            return fromBindingResult(bindException.getBindingResult());
        }
        if (e instanceof ConstraintViolationException constraintViolationException) {
            return constraintViolationException.getConstraintViolations().stream()
                    .map(violation -> new ValidationErrorResponse.FieldViolation(
                            lastPathSegment(violation.getPropertyPath().toString()),
                            defaultMessage(violation.getMessage())
                    ))
                    .distinct()
                    .toList();
        }
        if (e instanceof HandlerMethodValidationException handlerMethodValidationException) {
            return handlerMethodValidationException.getParameterValidationResults().stream()
                    .flatMap(result -> {
                        String parameterName = result.getMethodParameter().getParameterName();
                        String field = parameterName == null ? "$request" : parameterName;
                        return result.getResolvableErrors().stream()
                                .map(error -> new ValidationErrorResponse.FieldViolation(
                                        field,
                                        defaultMessage(error.getDefaultMessage())
                                ));
                    })
                    .distinct()
                    .toList();
        }
        return List.of(new ValidationErrorResponse.FieldViolation("$request", "요청값이 올바르지 않습니다."));
    }

    private List<ValidationErrorResponse.FieldViolation> fromBindingResult(BindingResult bindingResult) {
        List<ValidationErrorResponse.FieldViolation> fieldErrors = bindingResult.getFieldErrors().stream()
                .map(error -> new ValidationErrorResponse.FieldViolation(
                        error.getField(),
                        defaultMessage(error.getDefaultMessage())
                ))
                .toList();

        List<ValidationErrorResponse.FieldViolation> globalErrors = bindingResult.getGlobalErrors().stream()
                .map(error -> new ValidationErrorResponse.FieldViolation(
                        "$request",
                        defaultMessage(error.getDefaultMessage())
                ))
                .toList();

        return java.util.stream.Stream.concat(fieldErrors.stream(), globalErrors.stream())
                .distinct()
                .toList();
    }

    private String lastPathSegment(String propertyPath) {
        if (propertyPath == null || propertyPath.isBlank()) return "$request";
        int index = propertyPath.lastIndexOf('.');
        return index < 0 ? propertyPath : propertyPath.substring(index + 1);
    }

    private String defaultMessage(String message) {
        return message == null || message.isBlank() ? "유효하지 않은 값입니다." : message;
    }
}
