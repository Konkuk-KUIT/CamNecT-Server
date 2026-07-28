package CamNecT.server.domain.report.dto.response;

import CamNecT.server.domain.report.model.PenaltyType;

public record ReportResultResponse(
        Long reportId,
        String message,
        PenaltyType penaltyType
) {
    public static ReportResultResponse success(Long reportId, PenaltyType penaltyType) {
        String message = switch (penaltyType) {
            case WARNING -> "신고가 접수되었습니다. 경고 알림이 발송되었습니다.";
            case SUSPENDED_7_DAYS -> "신고가 접수되었습니다. 7일 서비스 이용 정지가 적용되었습니다.";
            case PERMANENT_BAN -> "신고가 접수되었습니다. 영구 차단 처리되었습니다.";
        };
        return new ReportResultResponse(reportId, message, penaltyType);
    }

    public static ReportResultResponse submitted(Long reportId) {
        return new ReportResultResponse(
                reportId,
                "성공적으로 제출되었습니다. 관리자 검토 후 처리되는 대로 알려드리겠습니다.",
                null
        );
    }
}
