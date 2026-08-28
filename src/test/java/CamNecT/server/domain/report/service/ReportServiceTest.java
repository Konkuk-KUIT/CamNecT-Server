package CamNecT.server.domain.report.service;

import CamNecT.server.domain.activity.service.ActivityService;
import CamNecT.server.domain.activity.service.RecruitmentService;
import CamNecT.server.domain.community.service.CommentService;
import CamNecT.server.domain.community.service.PostService;
import CamNecT.server.domain.report.dto.request.ReportCreateRequest;
import CamNecT.server.domain.report.dto.request.ReportProcessRequest;
import CamNecT.server.domain.report.model.*;
import CamNecT.server.domain.report.repository.ReportCaseRepository;
import CamNecT.server.domain.report.repository.ReportEvidenceRepository;
import CamNecT.server.domain.report.repository.ReportRepository;
import CamNecT.server.domain.report.repository.UserReportPenaltyRepository;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.ReportErrorCode;
import CamNecT.server.global.storage.dto.response.PresignDownloadResponse;
import CamNecT.server.global.storage.service.PresignEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportRepository reportRepository;
    @Mock ReportEvidenceRepository evidenceRepository;
    @Mock ReportCaseRepository reportCaseRepository;
    @Mock UserReportPenaltyRepository penaltyRepository;
    @Mock UserRepository userRepository;
    @Mock AccountAccessGuard accountAccessGuard;
    @Mock PresignEngine presignEngine;
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
                evidenceRepository,
                reportCaseRepository,
                penaltyRepository,
                userRepository,
                accountAccessGuard,
                presignEngine,
                reportAttachmentService,
                postService,
                commentService,
                activityService,
                recruitmentService,
                userReportPenaltyService,
                reportTargetResolver,
                Clock.fixed(Instant.parse("2026-07-28T03:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
        Users admin = user(9L, UserRole.ADMIN, UserStatus.ACTIVE);
        lenient().when(userRepository.existsByUserIdAndRole(9L, UserRole.ADMIN)).thenReturn(true);
        lenient().when(accountAccessGuard.requireAccessibleForUpdate(9L)).thenReturn(admin);
        lenient().when(reportTargetResolver.resolveForCreateLocked(anyLong(), any()))
                .thenAnswer(invocation -> reportTargetResolver.resolve(
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
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
        InOrder order = inOrder(
                accountAccessGuard,
                userRepository,
                reportTargetResolver,
                reportCaseRepository
        );
        order.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        order.verify(userRepository).lockUserRow(2L);
        order.verify(reportTargetResolver).resolveForCreateLocked(1L, request);
        order.verify(userRepository).findById(2L);
        order.verify(reportCaseRepository).findByTargetKey("COMMUNITY:100");
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
        InOrder order = inOrder(userRepository, accountAccessGuard, reportCaseRepository);
        order.verify(userRepository).lockUserRow(2L);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(3L);
        order.verify(userRepository).findById(2L);
        order.verify(reportCaseRepository).findByTargetKey("COMMUNITY:100");
        verify(reportCaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void newlySuspendedReporterIsRejectedBeforeCaseEvidenceOrReportMutation() {
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCreateRequest request = request(2L, 100L, TargetType.COMMUNITY);
        when(reportTargetResolver.resolve(1L, request))
                .thenReturn(new ReportTargetResolver.ResolvedTarget("COMMUNITY:100", 100L, author));
        doThrow(new CustomException(AuthErrorCode.USER_SUSPENDED))
                .when(accountAccessGuard).requireAccessibleForUpdate(1L);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createReport(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_SUSPENDED);
        verify(userRepository, never()).lockUserRow(2L);
        verifyNoInteractions(reportCaseRepository, reportRepository, reportAttachmentService);
    }

    @Test
    void reportMutationsUseReadCommittedAfterResolverOrCaseScalarReads() throws Exception {
        Transactional create = ReportService.class
                .getMethod("createReport", Long.class, ReportCreateRequest.class)
                .getAnnotation(Transactional.class);
        Transactional process = ReportService.class
                .getMethod("processReport", Long.class, Long.class, ReportProcessRequest.class)
                .getAnnotation(Transactional.class);

        assertThat(create.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        assertThat(process.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    @Test
    void existingCaseWithDifferentAuthorIsNotReused() {
        Users actualAuthor = user(2L, UserRole.USER, UserStatus.ACTIVE);
        Users forgedAuthor = user(3L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase forgedCase = reportCase(10L, forgedAuthor, 100L, TargetType.COMMUNITY);
        ReflectionTestUtils.setField(forgedCase, "targetKey", "COMMUNITY:100");
        ReportCreateRequest request = request(2L, 100L, TargetType.COMMUNITY);

        when(reportTargetResolver.resolve(1L, request))
                .thenReturn(new ReportTargetResolver.ResolvedTarget("COMMUNITY:100", 100L, actualAuthor));
        when(userRepository.findById(2L)).thenReturn(Optional.of(actualAuthor));
        when(reportCaseRepository.findByTargetKey("COMMUNITY:100")).thenReturn(Optional.of(forgedCase));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createReport(1L, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ReportErrorCode.REPORT_TARGET_INTEGRITY_UNVERIFIED);
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void quarantinedExistingCaseDoesNotAcceptNewSubmissions() {
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 100L, TargetType.COMMUNITY);
        ReflectionTestUtils.setField(
                reportCase,
                "moderationReason",
                "[TARGET_INTEGRITY_QUARANTINED] UNEXPECTED_OPEN_CASE_PENALTY"
        );
        ReportCreateRequest request = request(2L, 100L, TargetType.COMMUNITY);

        when(reportTargetResolver.resolve(1L, request))
                .thenReturn(new ReportTargetResolver.ResolvedTarget("COMMUNITY:100", 100L, author));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(reportCaseRepository.findByTargetKey("COMMUNITY:100")).thenReturn(Optional.of(reportCase));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createReport(1L, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ReportErrorCode.REPORT_TARGET_INTEGRITY_UNVERIFIED);
        verifyNoInteractions(reportRepository);
    }

    @Test
    void concurrentDuplicateConstraintIsMappedToDuplicateReport() {
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 100L, TargetType.COMMUNITY);
        ReportCreateRequest request = request(2L, 100L, TargetType.COMMUNITY);
        ConstraintViolationException constraintFailure = new ConstraintViolationException(
                "duplicate", new SQLException(), "uk_report_reporter_case_slot"
        );

        when(reportTargetResolver.resolve(1L, request))
                .thenReturn(new ReportTargetResolver.ResolvedTarget("COMMUNITY:100", 100L, author));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(reportCaseRepository.findByTargetKey("COMMUNITY:100")).thenReturn(Optional.of(reportCase));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate", constraintFailure));

        CustomException exception = assertThrows(CustomException.class, () -> service.createReport(1L, request));

        assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_DUPLICATE);
    }

    @Test
    void unrelatedIntegrityFailureIsNotMisclassifiedAsDuplicateReport() {
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 100L, TargetType.COMMUNITY);
        ReportCreateRequest request = request(2L, 100L, TargetType.COMMUNITY);
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "title too long",
                new ConstraintViolationException("invalid", new SQLException(), "some_other_constraint")
        );

        when(reportTargetResolver.resolve(1L, request))
                .thenReturn(new ReportTargetResolver.ResolvedTarget("COMMUNITY:100", 100L, author));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(reportCaseRepository.findByTargetKey("COMMUNITY:100")).thenReturn(Optional.of(reportCase));
        when(reportRepository.saveAndFlush(any(Report.class))).thenThrow(failure);

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.createReport(1L, request)
        );

        assertThat(exception).isSameAs(failure);
    }

    @Test
    void resolvedCaseRequiresAdministratorCategory() {
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
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 100L, TargetType.COMMUNITY);
        reportCase.reject(9L, "rejected", java.time.LocalDateTime.now());
        when(reportCaseRepository.findReportedUserIdByCaseId(10L)).thenReturn(Optional.of(2L));
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
        InOrder order = inOrder(userRepository, accountAccessGuard, reportCaseRepository);
        order.verify(userRepository).existsByUserIdAndRole(9L, UserRole.ADMIN);
        order.verify(reportCaseRepository).findReportedUserIdByCaseId(10L);
        order.verify(userRepository).lockUserRow(2L);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(9L);
        order.verify(reportCaseRepository).findByIdForUpdate(10L);
        verifyNoInteractions(userReportPenaltyService);
    }

    @Test
    void newlySuspendedAdminIsRejectedBeforeCaseOrContentMutation() {
        when(reportCaseRepository.findReportedUserIdByCaseId(10L)).thenReturn(Optional.of(2L));
        doThrow(new CustomException(AuthErrorCode.USER_SUSPENDED))
                .when(accountAccessGuard).requireAccessibleForUpdate(9L);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.processReport(
                        9L,
                        10L,
                        new ReportProcessRequest(ReportStatus.RESOLVED, ReportCategory.OTHER, null)
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_SUSPENDED);
        verify(reportCaseRepository, never()).findByIdForUpdate(10L);
        verifyNoInteractions(userReportPenaltyService, activityService, recruitmentService,
                postService, commentService);
    }

    @Test
    void resolvingCaseAppliesOnePenaltyAndFinalizesEverySubmission() {
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 2L, TargetType.USER);
        Report first = report(101L, reportCase, 1L);
        Report second = report(102L, reportCase, 3L);

        when(reportCaseRepository.findReportedUserIdByCaseId(10L)).thenReturn(Optional.of(2L));
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

    @Test
    void resolvingCommunityCaseUsesModerationDeletion() {
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 100L, TargetType.COMMUNITY);
        Report report = report(101L, reportCase, 1L);
        when(reportCaseRepository.findReportedUserIdByCaseId(10L)).thenReturn(Optional.of(2L));
        when(reportCaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reportCase));
        when(reportRepository.findAllByReportCase_CaseIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(report));
        when(userReportPenaltyService.applyPenalty(reportCase)).thenReturn(PenaltyType.WARNING);

        service.processReport(
                9L,
                10L,
                new ReportProcessRequest(ReportStatus.RESOLVED, ReportCategory.OTHER, "confirmed")
        );

        verify(postService).deleteForModeration(9L, 100L);
        verify(postService, never()).delete(anyLong(), anyLong());
        assertThat(reportCase.getStatus()).isEqualTo(ReportStatus.RESOLVED);
    }

    @Test
    void resolvingActivityCaseUsesModerationDeletionWithoutReenteringPublicGuard() {
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 100L, TargetType.ACTIVITY);
        Report report = report(101L, reportCase, 1L);
        when(reportCaseRepository.findReportedUserIdByCaseId(10L)).thenReturn(Optional.of(2L));
        when(reportCaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reportCase));
        when(reportRepository.findAllByReportCase_CaseIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(report));
        when(userReportPenaltyService.applyPenalty(reportCase)).thenReturn(PenaltyType.SUSPENDED_7_DAYS);

        service.processReport(
                9L,
                10L,
                new ReportProcessRequest(ReportStatus.RESOLVED, ReportCategory.OTHER, "confirmed")
        );

        verify(activityService).deleteForModeration(9L, 100L);
        verify(activityService, never()).delete(anyLong(), anyLong());
        assertThat(reportCase.getStatus()).isEqualTo(ReportStatus.RESOLVED);
    }

    @Test
    void unverifiedStoredTargetCannotBeApproved() {
        Users claimedAuthor = user(3L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, claimedAuthor, 100L, TargetType.COMMUNITY);
        Report report = report(101L, reportCase, 1L);
        when(reportCaseRepository.findReportedUserIdByCaseId(10L)).thenReturn(Optional.of(3L));
        when(reportCaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reportCase));
        when(reportRepository.findAllByReportCase_CaseIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(report));
        doThrow(new CustomException(ReportErrorCode.REPORT_TARGET_INTEGRITY_UNVERIFIED))
                .when(reportTargetResolver).validateStoredCase(reportCase, List.of(report));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.processReport(
                        9L,
                        10L,
                        new ReportProcessRequest(ReportStatus.RESOLVED, ReportCategory.OTHER, "confirmed")
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ReportErrorCode.REPORT_TARGET_INTEGRITY_UNVERIFIED);
        verifyNoInteractions(userReportPenaltyService);
        verifyNoInteractions(postService);
    }

    @Test
    void quarantinedCaseCannotBeApprovedEvenWhenStoredCoordinatesMatch() {
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 100L, TargetType.COMMUNITY);
        ReflectionTestUtils.setField(
                reportCase,
                "moderationReason",
                "[TARGET_INTEGRITY_QUARANTINED] UNEXPECTED_OPEN_CASE_PENALTY"
        );
        when(reportCaseRepository.findReportedUserIdByCaseId(10L)).thenReturn(Optional.of(2L));
        when(reportCaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reportCase));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.processReport(
                        9L,
                        10L,
                        new ReportProcessRequest(ReportStatus.RESOLVED, ReportCategory.OTHER, "confirmed")
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ReportErrorCode.REPORT_TARGET_INTEGRITY_UNVERIFIED);
        verifyNoInteractions(reportTargetResolver);
        verifyNoInteractions(userReportPenaltyService);
    }

    @Test
    void adminCanPresignSpecificEvidenceForSubmissionInCase() {
        Users admin = user(9L, UserRole.ADMIN, UserStatus.ACTIVE);
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 2L, TargetType.USER);
        Report report = new Report(
                reportCase,
                1L,
                2L,
                null,
                TargetType.USER,
                ReportCategory.OTHER,
                "title",
                "context"
        );
        ReflectionTestUtils.setField(report, "reportId", 101L);
        ReportEvidence evidence = ReportEvidence.create(
                report,
                "reports/1/evidence.png",
                "evidence.png",
                "image/png",
                1024L,
                0
        );
        ReflectionTestUtils.setField(evidence, "evidenceId", 1001L);
        PresignDownloadResponse expected = new PresignDownloadResponse(
                "https://s3.example/presigned",
                LocalDateTime.now(),
                "reports/1/evidence.png"
        );

        when(userRepository.findByUserId(9L)).thenReturn(Optional.of(admin));
        when(evidenceRepository.findByEvidenceIdAndReport_ReportIdAndReport_ReportCase_CaseId(1001L, 101L, 10L))
                .thenReturn(Optional.of(evidence));
        when(presignEngine.presignDownload("reports/1/evidence.png", "evidence.png", "image/png"))
                .thenReturn(expected);

        assertThat(service.getEvidenceDownloadUrl(9L, 10L, 101L, 1001L)).isEqualTo(expected);
    }

    @Test
    void createReportStoresMultipleEvidence() {
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 100L, TargetType.COMMUNITY);
        ReportCreateRequest request = new ReportCreateRequest(
                2L,
                100L,
                TargetType.COMMUNITY,
                ReportCategory.OTHER,
                "title",
                "context",
                List.of("temp/first.png", "temp/second.png")
        );

        when(reportTargetResolver.resolve(1L, request))
                .thenReturn(new ReportTargetResolver.ResolvedTarget("COMMUNITY:100", 100L, author));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(reportCaseRepository.findByTargetKey("COMMUNITY:100")).thenReturn(Optional.of(reportCase));
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "reportId", 101L);
            return saved;
        });
        when(reportAttachmentService.applyOnReportCreate(eq(1L), any(Report.class), eq(request.evidenceImageKeys())))
                .thenAnswer(invocation -> {
                    Report report = invocation.getArgument(1);
                    return List.of(
                            ReportEvidence.create(report, "final/first.png", "first.png", "image/png", 10L, 0),
                            ReportEvidence.create(report, "final/second.png", "second.png", "image/png", 20L, 1)
                    );
                });

        assertThat(service.createReport(1L, request)).isEqualTo(101L);

        verify(reportAttachmentService).applyOnReportCreate(
                eq(1L),
                any(Report.class),
                eq(List.of("temp/first.png", "temp/second.png"))
        );
    }

    @Test
    void reportDetailContainsEveryEvidenceItemInDisplayOrder() {
        Users admin = user(9L, UserRole.ADMIN, UserStatus.ACTIVE);
        Users author = user(2L, UserRole.USER, UserStatus.ACTIVE);
        ReportCase reportCase = reportCase(10L, author, 100L, TargetType.COMMUNITY);
        Report report = report(101L, reportCase, 1L);
        ReportEvidence first = ReportEvidence.create(
                report, "final/first.png", "first.png", "image/png", 100L, 0);
        ReportEvidence second = ReportEvidence.create(
                report, "final/second.jpg", "second.jpg", "image/jpeg", 200L, 1);
        ReflectionTestUtils.setField(first, "evidenceId", 1001L);
        ReflectionTestUtils.setField(second, "evidenceId", 1002L);

        when(userRepository.findByUserId(9L)).thenReturn(Optional.of(admin));
        when(reportCaseRepository.findById(10L)).thenReturn(Optional.of(reportCase));
        when(reportRepository.findAllByReportCase_CaseIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(report));
        when(evidenceRepository.findAllByReport_ReportIdInOrderByReport_ReportIdAscSortOrderAsc(List.of(101L)))
                .thenReturn(List.of(first, second));

        var detail = service.getReportDetail(9L, 10L);

        assertThat(detail.submissions()).singleElement().satisfies(submission -> {
            assertThat(submission.hasEvidence()).isTrue();
            assertThat(submission.evidenceCount()).isEqualTo(2);
            assertThat(submission.evidence()).extracting("evidenceId")
                    .containsExactly(1001L, 1002L);
        });
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
                "context"
        );
        ReflectionTestUtils.setField(report, "reportId", reportId);
        return report;
    }
}
