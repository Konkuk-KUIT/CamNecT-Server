package CamNecT.server.global.common.response;

import java.util.List;

public record ValidationErrorResponse(
        int status,
        int code,
        String message,
        List<FieldViolation> errors
) {
    public record FieldViolation(
            String field,
            String message
    ) {
    }
}
