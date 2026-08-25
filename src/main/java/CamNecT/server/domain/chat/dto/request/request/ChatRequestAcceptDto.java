package CamNecT.server.domain.chat.dto.request.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatRequestAcceptDto(
        @NotNull @Positive Long requestId,
        @NotNull Boolean isAccepted
) {
}
