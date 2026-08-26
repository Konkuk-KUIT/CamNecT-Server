package CamNecT.server.domain.gifticon.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "gifticon_export_batches",
        indexes = {
                @Index(name = "idx_gifticon_exported_at", columnList = "exported_at"),
                @Index(
                        name = "idx_gifticon_export_delivery_due",
                        columnList = "delivery_status,next_attempt_at,export_batch_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GifticonExportBatch {

    public static final int LAST_ERROR_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "export_batch_id")
    private Long id;

    @Column(name = "exported_at", nullable = false)
    private LocalDateTime exportedAt;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "item_count", nullable = false)
    private Integer itemCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 32)
    @Builder.Default
    private GifticonExportDeliveryStatus deliveryStatus = GifticonExportDeliveryStatus.READY;

    @Column(name = "delivery_attempt_count", nullable = false)
    @Builder.Default
    private Integer deliveryAttemptCount = 0;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isDue(LocalDateTime now) {
        return deliveryStatus == GifticonExportDeliveryStatus.READY
                && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
    }

    public void markSubmitted(LocalDateTime attemptedAt) {
        deliveryAttemptCount = currentAttemptCount() + 1;
        lastAttemptAt = attemptedAt;
        nextAttemptAt = null;
        submittedAt = attemptedAt;
        lastError = null;
        deliveryStatus = GifticonExportDeliveryStatus.SUBMITTED;
    }

    public void markDeliveryFailed(
            LocalDateTime attemptedAt,
            String error,
            int maxAttempts,
            LocalDateTime retryAt
    ) {
        deliveryAttemptCount = currentAttemptCount() + 1;
        lastAttemptAt = attemptedAt;
        submittedAt = null;
        lastError = truncate(error);

        if (deliveryAttemptCount >= maxAttempts) {
            deliveryStatus = GifticonExportDeliveryStatus.FAILED;
            nextAttemptAt = null;
            return;
        }

        deliveryStatus = GifticonExportDeliveryStatus.READY;
        nextAttemptAt = retryAt;
    }

    private int currentAttemptCount() {
        return deliveryAttemptCount == null ? 0 : deliveryAttemptCount;
    }

    private String truncate(String error) {
        if (error == null) return null;
        if (error.length() <= LAST_ERROR_MAX_LENGTH) return error;
        return error.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
