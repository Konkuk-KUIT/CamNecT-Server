package CamNecT.server.domain.report.service;

import CamNecT.server.domain.report.model.*;
import CamNecT.server.domain.report.repository.UserReportPenaltyRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.jwt.service.TokenSessionService;
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
    @Mock TokenSessionService tokenSessionService;

    private UserReportPenaltyService service;

    @BeforeEach
    void setUp() {
        service = new UserReportPenaltyService(
                penaltyRepository,
                userRepository,
                Clock.fixed(FIXED_INSTANT, SEOUL),
                tokenSessionService
        );
    }

    @Test
    void secondApprovedCaseCreatesSevenDayRestrictionWithoutChangingManualUserStatus() {
        Users user = user(2L, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, user, ReportCategory.OTHER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(penaltyRepository.countByUser_UserId(2L)).thenReturn(1L);

        PenaltyType result = service.applyPenalty(reportCase);

        ArgumentCaptor<UserReportPenalty> captor = ArgumentCaptor.forClass(UserReportPenalty.class);
        verify(penaltyRepository).saveAndFlush(captor.capture());
        UserReportPenalty saved = captor.getValue();

        assertThat(result).isEqualTo(PenaltyType.SUSPENDED_7_DAYS);
        verify(tokenSessionService).revokeAll(2L);
        assertThat(saved.getSuspensionEndDate()).isEqualTo(
                LocalDateTime.ofInstant(FIXED_INSTANT, SEOUL).plusDays(7)
        );
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).lockUserRow(2L);
    }

    @Test
    void immediateBanUsesAdministratorDecidedCategory() {
        Users user = user(2L, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, user, ReportCategory.FRAUD);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        PenaltyType result = service.applyPenalty(reportCase);

        assertThat(result).isEqualTo(PenaltyType.PERMANENT_BAN);
    }

    @Test
    void expiredReportRestrictionDoesNotClearExistingManualSuspension() {
        boolean reportRestricted = service.hasActiveRestriction(2L, UserStatus.SUSPENDED);

        assertThat(reportRestricted).isFalse();
        verify(penaltyRepository).existsActiveRestriction(
                eq(2L),
                eq(PenaltyType.PERMANENT_BAN),
                eq(PenaltyType.SUSPENDED_7_DAYS),
                any(LocalDateTime.class)
        );
    }

    @Test
    void activeTemporaryRestrictionIsEnforcedIndependentlyFromUserStatus() {
        when(penaltyRepository.existsActiveRestriction(
                eq(2L),
                eq(PenaltyType.PERMANENT_BAN),
                eq(PenaltyType.SUSPENDED_7_DAYS),
                any(LocalDateTime.class)
        )).thenReturn(true);

        assertThat(service.hasActiveRestriction(2L, UserStatus.ACTIVE)).isTrue();
    }

    @Test
    void permanentRestrictionIsAlwaysActive() {
        when(penaltyRepository.existsActiveRestriction(
                eq(2L),
                eq(PenaltyType.PERMANENT_BAN),
                eq(PenaltyType.SUSPENDED_7_DAYS),
                any(LocalDateTime.class)
        )).thenReturn(true);

        assertThat(service.hasActiveRestriction(2L, UserStatus.ACTIVE)).isTrue();
    }

    @Test
    void withdrawnAccountSkipsPenaltyLookup() {
        assertThat(service.hasActiveRestriction(2L, UserStatus.WITHDRAWN)).isFalse();

        verifyNoInteractions(penaltyRepository);
    }

    private static Users user(Long userId, UserStatus status) {
        return Users.builder().userId(userId).name("user").status(status).build();
    }

    private static ReportCase reportCase(Long caseId, Users user, ReportCategory decidedCategory) {
        ReportCase reportCase = ReportCase.open("USER:" + user.getUserId(), user, user.getUserId(), TargetType.USER);
        ReflectionTestUtils.setField(reportCase, "caseId", caseId);
        reportCase.decideCategory(decidedCategory);
        return reportCase;
    }
}
