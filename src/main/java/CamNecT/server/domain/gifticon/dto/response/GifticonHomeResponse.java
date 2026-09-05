package CamNecT.server.domain.gifticon.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record GifticonHomeResponse(
        long myPoint,
        String email,
        List<ProductView> products,
        LocalDateTime lastSyncedAt
) {
    public record ProductView(
            Long productId,
            String brandName,
            String productName,
            Integer pricePoints,
            String imageUrl,
            boolean active
    ) {}
}
