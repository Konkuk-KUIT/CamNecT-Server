package CamNecT.server.domain.verification.email.model;

import CamNecT.server.domain.users.model.Users;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Locale;

@Getter
@NoArgsConstructor
@Entity
@Table(
        name = "email_verification_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_email_verification_active_email",
                        columnNames = "active_email"
                )
        },
        indexes = {
                @Index(name = "idx_evt_user_used", columnList = "user_id, used_at"),
                @Index(name = "idx_evt_email_used_id", columnList = "email, used_at, id")
        }
)
public class EmailVerificationToken {
    private static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //send 단계에서 user가 없으므로 email이 기준키
    @Column(name = "email", nullable = false)
    private String email;

    /**
     * 미사용 토큰에만 정규화(trim/lower) 이메일을 두는 DB 유일성 키다. 사용 완료된
     * 토큰은 NULL을 공유할 수 있으므로 이력을 유지하면서도 이메일별 활성 토큰은 하나다.
     */
    @Column(name = "active_email")
    private String activeEmail;

    //verify 단계에서 user 객체 생성 후 주입
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Users user;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 생성일시

    protected EmailVerificationToken(
            String email,
            Users user,
            String codeHash,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        this.email = email;
        this.activeEmail = canonicalEmail(email);
        this.user = user; // null 가능
        this.codeHash = codeHash;
        this.createdAt = issuedAt;
        this.expiresAt = expiresAt;
        this.usedAt = null;
        this.attemptCount = 0;
    }

    public static EmailVerificationToken issueForEmail(String email, String rawCode, long expirationMinutes) {
        return issueForEmail(email, rawCode, expirationMinutes, LocalDateTime.now());
    }

    public static EmailVerificationToken issueForEmail(
            String email,
            String rawCode,
            long expirationMinutes,
            LocalDateTime issuedAt
    ) {
        return new EmailVerificationToken(
                email,
                null,
                EmailTokenUtil.sha256Hex(rawCode),
                issuedAt,
                issuedAt.plusMinutes(expirationMinutes)
        );
    }

    public void linkUser(Users user) {
        this.user = user;
    }

    public boolean isExpired() { return isExpired(LocalDateTime.now()); }
    public boolean isExpired(LocalDateTime now) { return now.isAfter(expiresAt); }
    public boolean isLocked() { return attemptCount >= MAX_ATTEMPTS; }

    public void markUsed() { markUsed(LocalDateTime.now()); }

    public void markUsed(LocalDateTime usedAt) {
        this.usedAt = usedAt;
        this.activeEmail = null;
    }

    public void increaseAttempt() { this.attemptCount++; }

    public boolean matchesCode(String rawCode) {
        return EmailTokenUtil.sha256Hex(rawCode).equals(this.codeHash);
    }

    @PrePersist
    @PreUpdate
    void synchronizeActiveEmail() {
        this.activeEmail = (usedAt == null) ? canonicalEmail(email) : null;
    }

    private String canonicalEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
