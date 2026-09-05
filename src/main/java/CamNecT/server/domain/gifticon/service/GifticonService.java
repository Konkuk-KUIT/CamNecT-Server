package CamNecT.server.domain.gifticon.service;

import CamNecT.server.domain.gifticon.dto.response.GifticonHomeResponse;
import CamNecT.server.domain.gifticon.dto.response.GifticonProductDetailResponse;
import CamNecT.server.domain.gifticon.model.GifticonProduct;
import CamNecT.server.domain.gifticon.repository.GifticonProductRepository;
import CamNecT.server.global.point.service.PointService;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.GifticonErrorCode;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GifticonService {

    private final GifticonProductRepository productRepository;
    private final PointService pointService;
    private final GifticonVendorClient vendorClient;

    @Value("${app.gifticon.vendor.enabled:false}")
    private boolean vendorEnabled;

    public enum Sort { POPULAR, PRICE_ASC, PRICE_DESC }

    public GifticonHomeResponse home(Long userId, Sort sort) {
        long myPoint = pointService.getBalance(userId);
        String myEmail = pointService.getEmail(userId);

        List<GifticonProduct> products = switch (sort) {
            case PRICE_ASC -> productRepository.findAllByIsActiveTrueOrderByPricePointsAscIdDesc();
            case PRICE_DESC -> productRepository.findAllByIsActiveTrueOrderByPricePointsDescIdDesc();
            default -> productRepository.findAllByIsActiveTrueOrderBySortScoreDescIdDesc();
        };

        LocalDateTime lastSyncedAt = products.stream()
                .map(GifticonProduct::getLastSyncedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        List<GifticonHomeResponse.ProductView> views = products.stream()
                .map(p -> new GifticonHomeResponse.ProductView(
                        p.getId(),
                        p.getBrandName(),
                        p.getProductName(),
                        p.getPricePoints(),
                        p.getImageUrl(),
                        Boolean.TRUE.equals(p.getIsActive())
                ))
                .toList();

        return new GifticonHomeResponse(myPoint, myEmail, views, lastSyncedAt);
    }

    public GifticonProductDetailResponse productDetail(Long productId) {
        GifticonProduct p = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(GifticonErrorCode.PRODUCT_NOT_FOUND));

        return new GifticonProductDetailResponse(
                p.getId(),
                p.getBrandName(),
                p.getProductName(),
                p.getPricePoints(),
                p.getImageUrl(),
                Boolean.TRUE.equals(p.getIsActive())
        );
    }

    /**
     * (스케줄러에서 호출) 업체 상품목록을 받아 DB 캐시 갱신
     */
    @Transactional
    public void syncCatalogFromVendor() {
        if (!vendorEnabled) return;

        LocalDateTime syncedAt = LocalDateTime.now();

        List<GifticonProduct.VendorSnapshot> vendorProducts;
        try {
            vendorProducts = vendorClient.fetchProducts();
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR, e);
        }

        // 업스트림이 일시적으로 비었을 때 "전체 비활성화" 방지
        if (vendorProducts == null || vendorProducts.isEmpty()) {
            return;
        }

        List<GifticonProduct> existing = productRepository.findAll();
        Map<String, GifticonProduct> existingMap = existing.stream()
                .collect(Collectors.toMap(GifticonProduct::getVendorProductCode, Function.identity(), (a, b) -> a));

        Set<String> seen = new HashSet<>();

        for (GifticonProduct.VendorSnapshot v : vendorProducts) {
            seen.add(v.vendorProductCode());

            GifticonProduct p = existingMap.get(v.vendorProductCode());
            if (p == null) {
                productRepository.save(GifticonProduct.builder()
                        .vendorProductCode(v.vendorProductCode())
                        .brandName(v.brandName())
                        .productName(v.productName())
                        .pricePoints(v.pricePoints())
                        .imageUrl(v.imageUrl())
                        .isActive(true)
                        .sortScore(v.sortScore() == null ? 0 : v.sortScore())
                        .lastSyncedAt(syncedAt)
                        .build());
            } else {
                p.updateFromVendor(v, syncedAt);
            }
        }

        // 이번 배치에 없던 상품 비활성화
        for (GifticonProduct p : existing) {
            if (!seen.contains(p.getVendorProductCode())) {
                p.deactivate(syncedAt);
            }
        }
    }
}
