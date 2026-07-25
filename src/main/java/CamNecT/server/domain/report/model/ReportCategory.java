package CamNecT.server.domain.report.model;

import lombok.Getter;

@Getter
public enum ReportCategory {
    BUSINESS_PROMOTION("영업 및 홍보", false),
    INSULT_DEFAMATION("욕설 및 비방", false),
    FALSE_INFORMATION("허위 사실 유포", false),
    NO_SHOW_ABANDONMENT("노쇼 및 잠수", false),
    HARASSMENT_THREAT("괴롭힘/협박", false),
    INAPPROPRIATE_PROFILE("부적절한 프로필 항목", false),
    SEXUAL_HARASSMENT("음란성 성희롱", true),
    FRAUD("사기 행위", true),
    OTHER("기타", false);

    private final String displayName;
    private final boolean isImmediateBan;

    ReportCategory(String displayName, boolean isImmediateBan) {
        this.displayName = displayName;
        this.isImmediateBan = isImmediateBan;
    }

}
