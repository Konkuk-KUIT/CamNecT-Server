package CamNecT.server.domain.report.model;

import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.model.UserStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "user_report_penalty",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_report_penalty_case",
                columnNames = "case_id"
        ),
        indexes = {
                @Index(name = "idx_user_report_penalty_user", columnList = "user_id"),
                @Index(name = "idx_user_report_penalty_active", columnList = "user_id, penalty_type, suspension_end_date")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserReportPenalty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "penalty_id")
    private Long penaltyId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private ReportCase reportCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", nullable = false, length = 30)
    private PenaltyType penaltyType;

    @Column(name = "suspension_end_date")
    private LocalDateTime suspensionEndDate;

    @Column(name = "reason", length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 30)
    private UserStatus previousStatus;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private UserReportPenalty(
            ReportCase reportCase,
            Users user,
            PenaltyType penaltyType,
            LocalDateTime suspensionEndDate,
            String reason
    ) {
        this.reportCase = reportCase;
        this.user = user;
        this.penaltyType = penaltyType;
        this.suspensionEndDate = suspensionEndDate;
        this.reason = reason;
        this.previousStatus = user.getStatus();
    }

    public static UserReportPenalty warning(ReportCase reportCase, Users user, String reason) {
        return new UserReportPenalty(reportCase, user, PenaltyType.WARNING, null, reason);
    }

    public static UserReportPenalty suspended(
            ReportCase reportCase,
            Users user,
            LocalDateTime suspensionEndDate,
            String reason
    ) {
        return new UserReportPenalty(
                reportCase,
                user,
                PenaltyType.SUSPENDED_7_DAYS,
                suspensionEndDate,
                reason
        );
    }

    public static UserReportPenalty permanentlyBanned(ReportCase reportCase, Users user, String reason) {
        return new UserReportPenalty(reportCase, user, PenaltyType.PERMANENT_BAN, null, reason);
    }

    public boolean isActiveAt(LocalDateTime now) {
        return penaltyType == PenaltyType.PERMANENT_BAN
                || (penaltyType == PenaltyType.SUSPENDED_7_DAYS
                && suspensionEndDate != null
                && now.isBefore(suspensionEndDate));
    }
}
