package CamNecT.server.domain.report.service;

import CamNecT.server.domain.activity.service.ActivityService;
import CamNecT.server.domain.activity.service.RecruitmentService;
import CamNecT.server.domain.community.service.CommentService;
import CamNecT.server.domain.community.service.PostService;
import CamNecT.server.domain.report.dto.request.ReportCreateRequest;
import CamNecT.server.domain.report.model.*;
import CamNecT.server.domain.report.repository.ReportRepository;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.ReportErrorCode;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportRepository reportRepository;
    @Mock UserRepository userRepository;
    @Mock PublicUrlIssuer publicUrlIssuer;
    @Mock ReportAttachmentService reportAttachmentService;
    @Mock PostService postService;
    @Mock CommentService commentService;
    @Mock ActivityService activityService;
    @Mock RecruitmentService recruitmentService;
    @Mock UserReportPenaltyService userReportPenaltyService;

    @InjectMocks ReportService service;

    @Test
    void sameReporterCannotReportSameTargetTwice() {
        ReportCreateRequest request = request(2L, 100L, TargetType.COMMUNITY);
        when(userRepository.findById(2L)).thenReturn(Optional.of(Users.builder().userId(2L).build()));
        when(reportRepository.existsByReporterIdAndTargetKey(1L, "COMMUNITY:100"))
                .thenReturn(true);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createReport(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_DUPLICATE);
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void userCannotReportSelf() {
        ReportCreateRequest request = request(1L, null, TargetType.USER);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createReport(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_SELF_NOT_ALLOWED);
        verifyNoInteractions(reportRepository);
    }

    @Test
    void alreadyProcessedReportCannotApplyPenaltyAgain() {
        Users admin = Users.builder().userId(9L).role(UserRole.ADMIN).build();
        Report report = report(10L, 2L);
        report.updateStatus(ReportStatus.RESOLVED);
        when(userRepository.findByUserId(9L)).thenReturn(Optional.of(admin));
        when(reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.processReport(9L, 10L, ReportStatus.RESOLVED)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_ALREADY_PROCESSED);
        verifyNoInteractions(userReportPenaltyService);
    }

    @Test
    void receivedIsNotAnAllowedAdminDecision() {
        Users admin = Users.builder().userId(9L).role(UserRole.ADMIN).build();
        when(userRepository.findByUserId(9L)).thenReturn(Optional.of(admin));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.processReport(9L, 10L, ReportStatus.RECEIVED)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_INVALID_STATUS);
        verify(reportRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void resolvedReportCreatesExactlyOnePenaltyAndFinalizesStatus() {
        Users admin = Users.builder().userId(9L).role(UserRole.ADMIN).build();
        Report report = report(10L, 2L);
        when(userRepository.findByUserId(9L)).thenReturn(Optional.of(admin));
        when(reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
        when(userReportPenaltyService.applyPenalty(report)).thenReturn(PenaltyType.WARNING);

        service.processReport(9L, 10L, ReportStatus.RESOLVED);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(report.getAppliedPenalty()).isEqualTo(PenaltyType.WARNING);
        verify(userReportPenaltyService, times(1)).applyPenalty(report);
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

    private static Report report(Long reportId, Long reportedUserId) {
        Report report = new Report(
                1L,
                reportedUserId,
                null,
                TargetType.USER,
                ReportCategory.OTHER,
                "title",
                "context",
                null
        );
        ReflectionTestUtils.setField(report, "reportId", reportId);
        return report;
    }
}
