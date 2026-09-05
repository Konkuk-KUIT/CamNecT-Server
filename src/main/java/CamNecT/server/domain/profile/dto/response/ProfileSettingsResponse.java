package CamNecT.server.domain.profile.dto.response;

public record ProfileSettingsResponse(
        Long userId,
        String name,
        String profileImageUrl,
        String email
) {
}
