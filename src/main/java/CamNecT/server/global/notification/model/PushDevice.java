package CamNecT.server.global.notification.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "push_devices",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_push_devices_user_device",
                        columnNames = {"user_id", "device_id"}
                ),
                @UniqueConstraint(
                        name = "uk_push_devices_active_token",
                        columnNames = "active_fcm_token"
                )
        },
        indexes = {
                @Index(name = "idx_push_devices_user_enabled", columnList = "user_id, enabled"),
                @Index(name = "idx_push_devices_token", columnList = "fcm_token")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PushDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_device_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id", nullable = false, length = 128)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private Platform platform;

    @Column(name = "fcm_token", nullable = false, length = 512)
    private String fcmToken;

    /**
     * Enabled 토큰에만 값을 두는 DB 유일성 키다. 비활성 행은 NULL을 공유할 수 있으므로
     * 디바이스 이력을 유지하면서도 하나의 FCM 토큰이 동시에 한 행에서만 활성화된다.
     */
    @Column(name = "active_fcm_token", length = 512)
    private String activeFcmToken;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    public void updateToken(Platform platform, String fcmToken) {
        this.platform = platform;
        this.fcmToken = fcmToken;
        this.activeFcmToken = fcmToken;
        this.enabled = true;
        this.lastSeenAt = LocalDateTime.now();
    }

    public void disable() {
        this.enabled = false;
        this.activeFcmToken = null;
        this.lastSeenAt = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    public void synchronizeActiveToken() {
        this.activeFcmToken = this.enabled ? this.fcmToken : null;
        if (this.lastSeenAt == null) this.lastSeenAt = LocalDateTime.now();
    }
}
