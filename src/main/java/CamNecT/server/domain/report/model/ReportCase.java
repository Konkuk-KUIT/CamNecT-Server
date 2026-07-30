package CamNecT.server.domain.report.model;

import CamNecT.server.domain.users.model.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "report_case",
        indexes = @Index(name = "idx_report_case_filter", columnList = "target_type, status, updated_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ReportCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "target_key", nullable = false, length = 100)
    private String targetKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_user_id", nullable = false)
    private Users reportedUser;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private TargetType targetType;

    @Column(name = "report_count", nullable = false)
    private long reportCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "decided_category", length = 40)
    private ReportCategory decidedCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "applied_penalty", length = 30)
    private PenaltyType appliedPenalty;

    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;

    @Column(name = "processed_by_admin_id")
    private Long processedByAdminId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private ReportCase(String targetKey, Users reportedUser, Long targetId, TargetType targetType) {
        this.targetKey = targetKey;
        this.reportedUser = reportedUser;
        this.targetId = targetId;
        this.targetType = targetType;
        this.reportCount = 0;
        this.status = ReportStatus.RECEIVED;
    }

    public static ReportCase open(String targetKey, Users reportedUser, Long targetId, TargetType targetType) {
        return new ReportCase(targetKey, reportedUser, targetId, targetType);
    }

    public void addReport() {
        this.reportCount++;
    }

    public void decideCategory(ReportCategory decidedCategory) {
        this.decidedCategory = decidedCategory;
    }

    public void resolve(
            Long adminId,
            ReportCategory decidedCategory,
            PenaltyType appliedPenalty,
            String moderationReason,
            LocalDateTime processedAt
    ) {
        this.status = ReportStatus.RESOLVED;
        this.decidedCategory = decidedCategory;
        this.appliedPenalty = appliedPenalty;
        this.moderationReason = moderationReason;
        this.processedByAdminId = adminId;
        this.processedAt = processedAt;
    }

    public void reject(Long adminId, String moderationReason, LocalDateTime processedAt) {
        this.status = ReportStatus.REJECTED;
        this.moderationReason = moderationReason;
        this.processedByAdminId = adminId;
        this.processedAt = processedAt;
    }
}
