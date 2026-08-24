package CamNecT.server.global.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminAnnouncementRequest(
        @NotBlank
        @Size(max = 255) // 현재 Notification.message가 길이 지정 없으면 DB가 255일 가능성 큼
        String message,

        @Size(max = 500)
        String link,

        @NotNull
        TargetType targetType,

        List<@NotNull @Positive Long> targetUserIds
) {
    public enum TargetType {
        ALL,
        USERS
    }
}
