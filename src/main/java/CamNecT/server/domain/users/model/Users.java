package CamNecT.server.domain.users.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "Users") // 테이블명
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 기본 생성자
@AllArgsConstructor
@Builder
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    //실질적 아이디라고 생각하시면 됩니다. -> 간단화해서 이메일을 아이디처럼..?
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    //일단 수집
    @Column(name = "phone_num", length = 20, unique = true)
    private String phoneNum;

    @Column(name = "email", unique = true)
    private String email;

    @Builder.Default //
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UserStatus status = UserStatus.ADMIN_PENDING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Builder.Default
    @Column(name = "verification_complete_pending", nullable = false)
    private boolean verificationCompletePending = false;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private UserSuspensionRecord suspensionRecord;

    @CreationTimestamp // 생성 시 자동으로 시간 입력
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp // 수정 시 자동으로 시간 업데이트
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;



    public void changeStatus(UserStatus newStatus) {
        this.status = newStatus;
    }

    public void updateName(String name) {
        if (name == null) return;
        String n = name.trim();
        if (!n.isBlank()) this.name = n;
    }

    public void changePasswordHash(String encoded) { this.passwordHash = encoded; }

    public void ensureSuspensionRecord() {
        if (this.suspensionRecord == null) {
            this.suspensionRecord = UserSuspensionRecord.builder()
                    .user(this)
                    .build();
        }
    }

    // 신고 관련 메서드들 (SuspensionRecord에 위임)
    public void incrementReportCount() {
        this.suspensionRecord.incrementReportCount();
    }

    public void applySuspension(LocalDateTime endDate) {
        this.suspensionRecord.applySuspension(endDate);
    }

    public void applyPermanentBan(String reason) {
        this.suspensionRecord.applyPermanentBan(reason);
    }

    public boolean isSuspended() {
        return suspensionRecord != null
                && (suspensionRecord.isPermanentlyBanned() || suspensionRecord.isSuspended());
    }

    public void clearSuspension() {
        suspensionRecord.clearSuspension();
    }

    public int getReportCount() {
        return suspensionRecord.getReportCount();
    }

    public boolean isPermanentlyBanned() {
        return suspensionRecord != null && suspensionRecord.isPermanentlyBanned();
    }

    //회원 탈퇴
    public void withdrawAnonymize(
            String name,
            String username,
            String email,
            String phone,
            UserStatus status
    ) {
        this.name = name;
        this.username = username;
        this.email = email;
        this.phoneNum = phone;
        this.status = status;
    }
}