package CamNecT.server.domain.gifticon.service;

import CamNecT.server.domain.gifticon.dto.request.ConfirmGifticonPurchaseRequest;
import CamNecT.server.domain.gifticon.dto.response.GifticonPurchaseConfirmResponse;
import CamNecT.server.domain.gifticon.model.GifticonProduct;
import CamNecT.server.domain.gifticon.model.GifticonPurchase;
import CamNecT.server.domain.gifticon.repository.GifticonProductRepository;
import CamNecT.server.domain.gifticon.repository.GifticonPurchaseRepository;
import CamNecT.server.domain.report.service.UserReportPenaltyService;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.global.point.model.PointEvent;
import CamNecT.server.global.point.service.PointService;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.GifticonErrorCode;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GifticonPurchaseService {

    private final GifticonProductRepository productRepository;
    private final GifticonPurchaseRepository purchaseRepository;
    private final UserRepository userRepository;
    private final PointService pointService;
    private final UserReportPenaltyService userReportPenaltyService;

    @Transactional
    public GifticonPurchaseConfirmResponse confirm(Long userId, ConfirmGifticonPurchaseRequest req) {

        userRepository.lockUserRow(userId);

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));

        // 1) 멱등 처리: (userId, clientRequestId)로 먼저 조회
        GifticonPurchase exists = purchaseRepository
                .findByUser_UserIdAndClientRequestId(userId, req.clientRequestId())
                .orElse(null);
        if (exists != null) {
            if (!matchesRequest(exists, req)) {
                throw new CustomException(GifticonErrorCode.DUPLICATE_REQUEST);
            }
            return new GifticonPurchaseConfirmResponse(exists.getId(), exists.getRequestedAt());
        }

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(AuthErrorCode.USER_WITHDRAWN);
        }
        if (user.getStatus() == UserStatus.SUSPENDED
                || userReportPenaltyService.hasActiveRestriction(userId, user.getStatus())) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }

        GifticonProduct product = productRepository.findById(req.productId())
                .orElseThrow(() -> new CustomException(GifticonErrorCode.PRODUCT_NOT_FOUND));

        if (!Boolean.TRUE.equals(product.getIsActive())) {
            throw new CustomException(GifticonErrorCode.PRODUCT_INACTIVE);
        }

        int qty = req.quantity();
        if (qty <= 0 || qty > 99) {
            throw new CustomException(GifticonErrorCode.INVALID_QUANTITY);
        }

        long expected = (long) product.getPricePoints() * qty;
        if (req.spendPoints().longValue() != expected) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }

        // 2) 구매요청 적재(스냅샷)
        GifticonPurchase purchase = GifticonPurchase.builder()
                .user(user)
                .product(product)
                .clientRequestId(req.clientRequestId())
                .quantity(qty)
                .unitPricePoints(product.getPricePoints())
                .totalPricePoints((int) expected)

                .buyerName(user.getName())
                .buyerPhone(user.getPhoneNum())  // Users에 없으면 null 처리/필드명 수정
                .buyerEmail(user.getEmail())  // Users에 없으면 null 처리/필드명 수정

                .recipientName(blankToNull(req.recipientName()))
                .recipientPhone(blankToNull(req.recipientPhone()))
                .giftMessage(blankToNull(req.giftMessage()))
                .requestedAt(LocalDateTime.now())
                .build();

        try {
            purchaseRepository.saveAndFlush(purchase);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(GifticonErrorCode.DUPLICATE_REQUEST, e);
        }

        // 3) 포인트 차감 (PointService 사용)
        // 구매 레코드가 clientRequestId 멱등성을 보장하므로 포인트 이벤트는 길이가 제한된 purchaseId를 사용합니다.
        PointEvent event = PointEvent.gifticonPurchase(userId, purchase.getId());
        pointService.spendPoint(userId, (int) expected, event);

        return new GifticonPurchaseConfirmResponse(purchase.getId(), purchase.getRequestedAt());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static boolean matchesRequest(GifticonPurchase purchase, ConfirmGifticonPurchaseRequest req) {
        return Objects.equals(purchase.getProduct().getId(), req.productId())
                && Objects.equals(purchase.getQuantity(), req.quantity())
                && Objects.equals(purchase.getTotalPricePoints(), req.spendPoints())
                && Objects.equals(purchase.getRecipientName(), blankToNull(req.recipientName()))
                && Objects.equals(purchase.getRecipientPhone(), blankToNull(req.recipientPhone()))
                && Objects.equals(purchase.getGiftMessage(), blankToNull(req.giftMessage()));
    }
}
