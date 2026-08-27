package CamNecT.server.domain.activity.model.recruitment;

import CamNecT.server.domain.activity.dto.TeamRecruitmentDto;
import CamNecT.server.domain.activity.dto.request.RecruitmentRequest;
import CamNecT.server.domain.activity.model.enums.RecruitStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "team_recruitments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class TeamRecruitment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recruitId;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @JoinColumn(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RecruitStatus recruitStatus = RecruitStatus.RECRUITING;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private LocalDate recruitDeadline;

    @Builder.Default
    private Integer recruitCount = 1;

    @Builder.Default
    private Integer bookmarkCount = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // 북마크 수 증가
    public void incrementBookmarkCount() {
        this.bookmarkCount++;
    }

    // 북마크 수 감소
    public void decrementBookmarkCount() {
        if (this.bookmarkCount > 0) {
            this.bookmarkCount--;
        }
    }

    // 모집 마감
    public void close() {
        this.recruitStatus = RecruitStatus.CLOSED;
    }

    // 모집글 수정
    public void update(RecruitmentRequest request) {
        this.title = request.title();
        this.content = request.content();
        this.recruitCount = request.recruitCount();
        this.recruitDeadline = request.recruitDeadline();
    }

    public TeamRecruitmentDto toDto(String activityTitle, String userName) {
        return new TeamRecruitmentDto(
                this.recruitId,
                activityTitle, // 외부 서비스/레포지토리에서 조회 필요
                userName,      // 외부 서비스/레포지토리에서 조회 필요
                this.recruitStatus.name(),
                this.title,
                this.content,
                this.recruitDeadline,
                this.recruitCount,
                this.bookmarkCount,
                this.createdAt,
                this.updatedAt
        );
    }
}
