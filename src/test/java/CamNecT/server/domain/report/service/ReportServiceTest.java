package CamNecT.server.domain.report.service;

import CamNecT.server.domain.activity.service.ActivityService;
import CamNecT.server.domain.activity.service.RecruitmentService;
import CamNecT.server.domain.community.service.CommentService;
import CamNecT.server.domain.community.service.PostService;
import CamNecT.server.domain.report.dto.request.ReportCreateRequest;
import CamNecT.server.domain.report.dto.request.ReportProcessRequest;
import CamNecT.server.domain.report.model.*;
import CamNecT.server.domain.report.repository.ReportCaseRepository;
import CamNecT.server.domain.report.repository.ReportRepository;
import CamNecT.server.domain.report.repository.UserReportPenaltyRepository;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.ReportErrorCode;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportRepository reportRepository;
    @Mock ReportCaseRepository reportCaseRepository;
    @Mock UserReportPenaltyRepository penaltyRepository;
    @Mock UserRepository userRepository;
    @Mock PublicUrlIssuer publicUrlIssuer;
    @Mock ReportAttachmentService reportAttachmentService;
    @Mock PostService postService;
    @Mock CommentService commentService;
    @Mock ActivityService activityService;
    @Mock RecruitmentService recruitmentService;
    @Mock UserReportPenaltyService userReportPenaltyService;
    @Mock ReportTargetResolver reportTargetResolver;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(
                reportRepository,
                reportCaseRepository,
                penaltyRepository,
                userRepository,
                publicUrlIssuer,
                reportAttachmentService,
                postService,
                commentService,
                activityService,
                recruitmentService,
                userReportPenaltyService,
                reportTargetResolver,
                Clock.fixed(Instant.parse("2026-07-28T03:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
    }

    @Test
    void sameReporterCannotSubmitTwiceToSameCase() {
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 100L, TargetType.COMMUNITY);
        ReportCreateRequest request = request(2L, 100L, TargetType.COMMUNITY);

        when(reportTargetResolver.resolve(1L, request))
                .thenReturn(new ReportTargetResolver.ResolvedTarget("COMMUNITY:100", 100L, author));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(reportCaseRepository.findByTargetKey("COMMUNITY:100")).thenReturn(Optional.of(reportCase));
        when(reportRepository.existsByReporterIdAndReportCase_CaseId(1L, 10L)).thenReturn(true);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createReport(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_DUPLICATE);
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void differentReportersAreAggregatedIntoExistingTargetCase() {
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 100L, TargetType.COMMUNITY);
        reportCase.addReport();
        ReportCreateRequest request = request(2L, 100L, TargetType.COMMUNITY);

        when(reportTargetResolver.resolve(3L, request))
                .thenReturn(new ReportTargetResolver.ResolvedTarget("COMMUNITY:100", 100L, author));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(reportCaseRepository.findByTargetKey("COMMUNITY:100")).thenReturn(Optional.of(reportCase));
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "reportId", 101L);
            return saved;
        });

        Long reportId = service.createReport(3L, request);

        assertThat(reportId).isEqualTo(101L);
        assertThat(reportCase.getReportCount()).isEqualTo(2L);
        verify(reportCaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolvedCaseRequiresAdministratorCategory() {
        Users admin = user(9L, UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findByUserId(9L)).thenReturn(Optional.of(admin));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.processReport(
                        9L,
                        10L,
                        new ReportProcessRequest(ReportStatus.RESOLVED, null, null)
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_CATEGORY_REQUIRED);
        verify(reportCaseRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void alreadyProcessedCaseCannotApplyPenaltyAgain() {
        Users admin = user(9L, UserRole.ADMIN, UserStatus.ACTIVE);
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 100L, TargetType.COMMUNITY);
        reportCase.reject(9L, "rejected", java.time.LocalDateTime.now());
        when(userRepository.findByUserId(9L)).thenReturn(Optional.of(admin));
        when(reportCaseRepository.findById(10L)).thenReturn(Optional.of(reportCase));
        when(reportCaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reportCase));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.processReport(
                        9L,
                        10L,
                        new ReportProcessRequest(ReportStatus.RESOLVED, ReportCategory.OTHER, null)
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_ALREADY_PROCESSED);
        verifyNoInteractions(userReportPenaltyService);
    }

    @Test
    void resolvingCaseAppliesOnePenaltyAndFinalizesEverySubmission() {
        Users admin = user(9L, UserRole.ADMIN, UserStatus.ACTIVE);
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 2L, TargetType.USER);
        Report first = report(101L, reportCase, 1L);
        Report second = report(102L, reportCase, 3L);

        when(userRepository.findByUserId(9L)).thenReturn(Optional.of(admin));
        when(reportCaseRepository.findById(10L)).thenReturn(Optional.of(reportCase));
        when(reportCaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reportCase));
        when(reportRepository.findAllByReportCase_CaseIdOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(first, second));
        when(userReportPenaltyService.applyPenalty(reportCase)).thenReturn(PenaltyType.WARNING);

        service.processReport(
                9L,
                10L,
                new ReportProcessRequest(ReportStatus.RESOLVED, ReportCategory.OTHER, "confirmed")
        );

        assertThat(reportCase.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(reportCase.getDecidedCategory()).isEqualTo(ReportCategory.OTHER);
        assertThat(first.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(second.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        verify(userReportPenaltyService, times(1)).applyPenalty(reportCase);
    }

    private static ReportCreateRequest request(Long reportedUserId, Long postId, TargetType targetType) {
        return new ReportCreateRequest(
                reportedUserId,
                postId,
                targetType,
                ReportCategory.OTHER,
                "title",
                "context",
                null
        );
    }

    private static Users user(Long userId, UserRole role, UserStatus status) {
        return Users.builder()
                .userId(userId)
                .name("user-" + userId)
                .role(role)
                .status(status)
                .build();
    }

    private static ReportCase reportCase(Long caseId, Users author, Long targetId, TargetType targetType) {
        ReportCase reportCase = ReportCase.open(
                Report.targetKeyFor(targetType, author.getUserId(), targetId),
                author,
                targetId,
                targetType
        );
        ReflectionTestUtils.setField(reportCase, "caseId", caseId);
        return reportCase;
    }

    private static Report report(Long reportId, ReportCase reportCase, Long reporterId) {
        Report report = new Report(
                reportCase,
                reporterId,
                reportCase.getReportedUser().getUserId(),
                reportCase.getTargetType() == TargetType.USER ? null : reportCase.getTargetId(),
                reportCase.getTargetType(),
                ReportCategory.OTHER,
                "title",
                "context",
                null
        );
        ReflectionTestUtils.setField(report, "reportId", reportId);
        return report;
    }
}
