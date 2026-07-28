package CamNecT.server.domain.report.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "report",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_report_reporter_case_slot",
                columnNames = {"reporter_id", "case_id", "submission_slot"}
        ),
        indexes = @Index(name = "idx_report_case_created", columnList = "case_id, created_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private ReportCase reportCase;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId; // 신고자 ID

    @Column(name = "reported_user_id", nullable = false)
    private Long reportedUserId; // 신고당한 유저 ID

    @Column(name = "reported_post_id")
    private Long reportedPostId; // 신고 글 ID (NULL 허용)

    @Column(name = "target_key", nullable = false, length = 100)
    private String targetKey;

    @Column(name = "submission_slot", nullable = false)
    private long submissionSlot;

    @Enumerated(EnumType.STRING)
    private TargetType postType; // 신고 글 타입 (COMMUNITY, ACTIVITY, USER 등)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportCategory reportCategory; // 신고 사유 카테고리

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String context;

    private String evidenceImageUrl; // 증거 이미지 URL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.RECEIVED; // 기본값 RECEIVED

    @Enumerated(EnumType.STRING)
    private PenaltyType appliedPenalty; // 적용된 패널티

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 편의를 위한 빌더 패턴 또는 생성자
    public Report(ReportCase reportCase, Long reporterId, Long reportedUserId, Long reportedPostId,
                  TargetType postType, ReportCategory reportCategory, String title, String context, String evidenceImageUrl) {
        this.reportCase = reportCase;
        this.reporterId = reporterId;
        this.reportedUserId = reportedUserId;
        this.reportedPostId = reportedPostId;
        this.postType = postType;
        this.targetKey = targetKeyFor(postType, reportedUserId, reportedPostId);
        this.submissionSlot = 0;
        this.reportCategory = reportCategory;
        this.title = title;
        this.context = context;
        this.evidenceImageUrl = evidenceImageUrl;
        this.status = ReportStatus.RECEIVED;
    }

    public void updateStatus(ReportStatus status) {
        this.status = status;
    }

    public void applyPenalty(PenaltyType penalty) {
        this.appliedPenalty = penalty;
    }

    public void updateEvidenceImageUrl(String evidenceImageUrl) {
        this.evidenceImageUrl = evidenceImageUrl;
    }

    public static String targetKeyFor(TargetType postType, Long reportedUserId, Long reportedPostId) {
        Long targetId = postType == TargetType.USER ? reportedUserId : reportedPostId;
        return postType.name() + ":" + targetId;
    }
}
