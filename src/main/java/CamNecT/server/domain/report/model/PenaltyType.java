package CamNecT.server.domain.report.model;

import lombok.Getter;

@Getter
public enum PenaltyType {
    WARNING("경고"),
    SUSPENDED_7_DAYS("7일 정지"),
    PERMANENT_BAN("영구 차단");

    private final String displayName;

    PenaltyType(String displayName) {
        this.displayName = displayName;
    }

}
