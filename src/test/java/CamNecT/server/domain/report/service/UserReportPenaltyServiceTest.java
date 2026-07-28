package CamNecT.server.domain.report.service;

import CamNecT.server.domain.report.model.*;
import CamNecT.server.domain.report.repository.UserReportPenaltyRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserReportPenaltyServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-28T03:00:00Z");

    @Mock UserReportPenaltyRepository penaltyRepository;
    @Mock UserRepository userRepository;

    private UserReportPenaltyService service;

    @BeforeEach
    void setUp() {
        service = new UserReportPenaltyService(
                penaltyRepository,
                userRepository,
                Clock.fixed(FIXED_INSTANT, SEOUL)
        );
    }

    @Test
    void secondApprovedReportCreatesSevenDaySuspensionAndUpdatesUserStatus() {
        Users user = activeUser(2L);
        Report report = report(10L, 2L, ReportCategory.OTHER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(penaltyRepository.countByUser_UserId(2L)).thenReturn(1L);

        PenaltyType result = service.applyPenalty(report);

        ArgumentCaptor<UserReportPenalty> captor = ArgumentCaptor.forClass(UserReportPenalty.class);
        verify(penaltyRepository).saveAndFlush(captor.capture());
        UserReportPenalty saved = captor.getValue();

        assertThat(result).isEqualTo(PenaltyType.SUSPENDED_7_DAYS);
        assertThat(saved.getPenaltyType()).isEqualTo(PenaltyType.SUSPENDED_7_DAYS);
        assertThat(saved.getSuspensionEndDate()).isEqualTo(
                LocalDateTime.ofInstant(FIXED_INSTANT, SEOUL).plusDays(7)
        );
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(userRepository).lockUserRow(2L);
    }

    @Test
    void immediateBanCreatesPermanentPenaltyOnFirstApprovedReport() {
        Users user = activeUser(2L);
        Report report = report(10L, 2L, ReportCategory.FRAUD);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(penaltyRepository.countByUser_UserId(2L)).thenReturn(0L);

        PenaltyType result = service.applyPenalty(report);

        ArgumentCaptor<UserReportPenalty> captor = ArgumentCaptor.forClass(UserReportPenalty.class);
        verify(penaltyRepository).saveAndFlush(captor.capture());
        assertThat(result).isEqualTo(PenaltyType.PERMANENT_BAN);
        assertThat(captor.getValue().getPenaltyType()).isEqualTo(PenaltyType.PERMANENT_BAN);
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void expiredTemporaryPenaltyRestoresStatusFromBeforeSuspension() {
        Users user = Users.builder().userId(2L).status(UserStatus.ADMIN_PENDING).build();
        Report report = report(10L, 2L, ReportCategory.OTHER);
        UserReportPenalty expiredPenalty = UserReportPenalty.suspended(
                report,
                user,
                LocalDateTime.ofInstant(FIXED_INSTANT, SEOUL).minusMinutes(1),
                "expired"
        );
        user.changeStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(penaltyRepository.findTopByUser_UserIdAndPenaltyTypeOrderBySuspensionEndDateDesc(
                2L,
                PenaltyType.SUSPENDED_7_DAYS
        )).thenReturn(Optional.of(expiredPenalty));

        boolean suspended = service.refreshRestrictionStatus(2L);

        assertThat(suspended).isFalse();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ADMIN_PENDING);
    }

    @Test
    void permanentPenaltyCanNeverBeCleared() {
        Users user = activeUser(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(penaltyRepository.existsByUser_UserIdAndPenaltyType(2L, PenaltyType.PERMANENT_BAN))
                .thenReturn(true);

        boolean suspended = service.refreshRestrictionStatus(2L);

        assertThat(suspended).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(penaltyRepository, never())
                .findTopByUser_UserIdAndPenaltyTypeOrderBySuspensionEndDateDesc(anyLong(), any());
    }

    @Test
    void unrelatedSuspendedStatusIsNotClearedWithoutReportPenaltyHistory() {
        Users user = Users.builder().userId(2L).status(UserStatus.SUSPENDED).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(penaltyRepository.findTopByUser_UserIdAndPenaltyTypeOrderBySuspensionEndDateDesc(
                2L,
                PenaltyType.SUSPENDED_7_DAYS
        )).thenReturn(Optional.empty());

        boolean suspended = service.refreshRestrictionStatus(2L);

        assertThat(suspended).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    private static Users activeUser(Long userId) {
        return Users.builder().userId(userId).status(UserStatus.ACTIVE).build();
    }

    private static Report report(Long reportId, Long reportedUserId, ReportCategory category) {
        Report report = new Report(
                1L,
                reportedUserId,
                null,
                TargetType.USER,
                category,
                "title",
                "context",
                null
        );
        ReflectionTestUtils.setField(report, "reportId", reportId);
        return report;
    }
}
