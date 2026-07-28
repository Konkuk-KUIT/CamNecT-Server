package CamNecT.server.domain.report.dto.response;

import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;

public record ReportTargetUserResponse(
        Long userId,
        String name,
        UserStatus status
) {
    public static ReportTargetUserResponse from(Users user) {
        return new ReportTargetUserResponse(user.getUserId(), user.getName(), user.getStatus());
    }
}
