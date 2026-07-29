package CamNecT.server.domain.report.service;

import CamNecT.server.domain.report.model.PenaltyType;
import CamNecT.server.domain.report.model.ReportCase;
import CamNecT.server.domain.report.model.UserReportPenalty;
import CamNecT.server.domain.report.repository.UserReportPenaltyRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.ReportErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import CamNecT.server.global.jwt.service.TokenSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UserReportPenaltyService {

    private final UserReportPenaltyRepository penaltyRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final TokenSessionService tokenSessionService;

    @Transactional
    public PenaltyType applyPenalty(ReportCase reportCase) {
        Long userId = reportCase.getReportedUser().getUserId();
        userRepository.lockUserRow(userId);

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        if (penaltyRepository.existsByReportCase_CaseId(reportCase.getCaseId())) {
            throw new CustomException(ReportErrorCode.REPORT_ALREADY_PROCESSED);
        }

        long approvedReportCount = penaltyRepository.countByUser_UserId(userId) + 1;
        LocalDateTime now = LocalDateTime.now(clock);
        UserReportPenalty penalty = determinePenalty(reportCase, user, approvedReportCount, now);

        try {
            penaltyRepository.saveAndFlush(penalty);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ReportErrorCode.REPORT_ALREADY_PROCESSED);
        }

        if (penalty.getPenaltyType() != PenaltyType.WARNING) {
            revokeSafely(userId);
        }

        return penalty.getPenaltyType();
    }

    @Transactional(readOnly = true)
    public boolean hasActiveRestriction(Long userId) {
        UserStatus status = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND))
                .getStatus();

        if (status == UserStatus.WITHDRAWN) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        boolean permanentlyBanned = penaltyRepository.existsByUser_UserIdAndPenaltyType(
                userId,
                PenaltyType.PERMANENT_BAN
        );
        boolean temporarilySuspended = penaltyRepository
                .existsByUser_UserIdAndPenaltyTypeAndSuspensionEndDateAfter(
                        userId,
                        PenaltyType.SUSPENDED_7_DAYS,
                        now
                );

        return permanentlyBanned || temporarilySuspended;
    }

    public long countPenalties(Long userId) {
        return penaltyRepository.countByUser_UserId(userId);
    }

    private UserReportPenalty determinePenalty(
            ReportCase reportCase,
            Users user,
            long approvedReportCount,
            LocalDateTime now
    ) {
        if (reportCase.getDecidedCategory().isImmediateBan()) {
            return UserReportPenalty.permanentlyBanned(
                    reportCase,
                    user,
                    "관리자 확정 즉시 제재 대상: " + reportCase.getDecidedCategory().getDisplayName()
            );
        }

        if (approvedReportCount == 1) {
            return UserReportPenalty.warning(reportCase, user, "신고 객체 1건 승인");
        }

        if (approvedReportCount == 2) {
            return UserReportPenalty.suspended(
                    reportCase,
                    user,
                    now.plusDays(7),
                    "승인된 신고 객체 누적 2건"
            );
        }

        return UserReportPenalty.permanentlyBanned(
                reportCase,
                user,
                "승인된 신고 객체 누적 3건 이상"
        );
    }

    private void revokeSafely(Long userId) {
        try {
            tokenSessionService.revoke(userId);
        } catch (RuntimeException e) {
            log.error("Penalty was saved, but token session revocation failed for userId={}", userId, e);
        }
    }
}
