package CamNecT.server.domain.report.service;

import CamNecT.server.domain.report.model.PenaltyType;
import CamNecT.server.domain.report.model.Report;
import CamNecT.server.domain.report.model.UserReportPenalty;
import CamNecT.server.domain.report.repository.UserReportPenaltyRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.ReportErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserReportPenaltyService {

    private final UserReportPenaltyRepository penaltyRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public PenaltyType applyPenalty(Report report) {
        Long userId = report.getReportedUserId();
        userRepository.lockUserRow(userId);

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        if (penaltyRepository.existsByReport_ReportId(report.getReportId())) {
            throw new CustomException(ReportErrorCode.REPORT_ALREADY_PROCESSED);
        }

        long approvedReportCount = penaltyRepository.countByUser_UserId(userId) + 1;
        LocalDateTime now = LocalDateTime.now(clock);
        UserReportPenalty penalty = determinePenalty(report, user, approvedReportCount, now);

        try {
            penaltyRepository.saveAndFlush(penalty);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ReportErrorCode.REPORT_ALREADY_PROCESSED);
        }

        PenaltyType penaltyType = penalty.getPenaltyType();
        if ((penaltyType == PenaltyType.SUSPENDED_7_DAYS
                || penaltyType == PenaltyType.PERMANENT_BAN)
                && user.getStatus() != UserStatus.WITHDRAWN) {
            user.changeStatus(UserStatus.SUSPENDED);
        }

        return penaltyType;
    }

    @Transactional
    public boolean refreshRestrictionStatus(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAWN) {
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

        if (permanentlyBanned || temporarilySuspended) {
            if (user.getStatus() != UserStatus.SUSPENDED) {
                user.changeStatus(UserStatus.SUSPENDED);
            }
            return true;
        }

        Optional<UserReportPenalty> latestTemporaryPenalty = penaltyRepository
                .findTopByUser_UserIdAndPenaltyTypeOrderBySuspensionEndDateDesc(
                        userId,
                        PenaltyType.SUSPENDED_7_DAYS
                );

        if (latestTemporaryPenalty.isPresent() && user.getStatus() == UserStatus.SUSPENDED) {
            UserStatus previousStatus = latestTemporaryPenalty.get().getPreviousStatus();
            user.changeStatus(previousStatus == UserStatus.SUSPENDED ? UserStatus.ACTIVE : previousStatus);
            return false;
        }

        return user.getStatus() == UserStatus.SUSPENDED;
    }

    public long countPenalties(Long userId) {
        return penaltyRepository.countByUser_UserId(userId);
    }

    private UserReportPenalty determinePenalty(
            Report report,
            Users user,
            long approvedReportCount,
            LocalDateTime now
    ) {
        if (report.getReportCategory().isImmediateBan()) {
            return UserReportPenalty.permanentlyBanned(
                    report,
                    user,
                    "즉시 제재 대상: " + report.getReportCategory().getDisplayName()
            );
        }

        if (approvedReportCount == 1) {
            return UserReportPenalty.warning(report, user, "신고 1회 승인");
        }

        if (approvedReportCount == 2) {
            return UserReportPenalty.suspended(
                    report,
                    user,
                    now.plusDays(7),
                    "신고 누적 2회"
            );
        }

        return UserReportPenalty.permanentlyBanned(
                report,
                user,
                "신고 누적 3회 이상"
        );
    }
}
