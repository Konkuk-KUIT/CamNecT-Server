package CamNecT.server.domain.gifticon.model;

import CamNecT.server.domain.users.model.Users;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "gifticon_purchases",
        uniqueConstraints = @UniqueConstraint(name = "uk_gifticon_purchase_client_req", columnNames = {"user_id", "client_request_id"}),
        indexes = {
                @Index(
                        name = "idx_gifticon_purchase_export_queue",
                        columnList = "export_batch_id,requested_at,purchase_id"
                ),
                @Index(name = "idx_gifticon_purchase_user", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GifticonPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private GifticonProduct product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "export_batch_id")
    private GifticonExportBatch exportBatch;

    @Column(name = "client_request_id", length = 100)
    private String clientRequestId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_points", nullable = false)
    private Integer unitPricePoints;

    @Column(name = "total_price_points", nullable = false)
    private Integer totalPricePoints;

    @Column(name = "buyer_name", nullable = false, length = 100)
    private String buyerName;

    @Column(name = "buyer_email", length = 255)
    private String buyerEmail;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    // 기존 주문의 수신 이메일을 확인할 수 없으면 null로 유지한다.
    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "gift_message", length = 500)
    private String giftMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "exported_at")
    private LocalDateTime exportedAt;

    @Column(name = "admin_processed_at")
    private LocalDateTime adminProcessedAt;

    @Column(name = "admin_success")
    private Boolean adminSuccess;

    @Column(name = "admin_memo", length = 500)
    private String adminMemo;

    public boolean isExported() {
        return exportBatch != null;
    }

    public void markExported(GifticonExportBatch batch, LocalDateTime exportedAt) {
        this.exportBatch = batch;
        this.exportedAt = exportedAt;
    }
}
