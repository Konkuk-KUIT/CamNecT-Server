package CamNecT.server.domain.gifticon.service;

import CamNecT.server.domain.gifticon.dto.request.ConfirmGifticonPurchaseRequest;
import CamNecT.server.domain.gifticon.model.GifticonProduct;
import CamNecT.server.domain.gifticon.model.GifticonPurchase;
import CamNecT.server.domain.gifticon.repository.GifticonProductRepository;
import CamNecT.server.domain.gifticon.repository.GifticonPurchaseRepository;
import CamNecT.server.domain.report.service.UserReportPenaltyService;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.GifticonErrorCode;
import CamNecT.server.global.point.service.PointService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GifticonPurchaseServiceTest {

    @Mock GifticonProductRepository productRepository;
    @Mock GifticonPurchaseRepository purchaseRepository;
    @Mock UserRepository userRepository;
    @Mock PointService pointService;
    @Mock UserReportPenaltyService userReportPenaltyService;

    @InjectMocks GifticonPurchaseService service;

    @Test
    void sameIdempotencyKeyAndSameRequestReturnsExistingPurchase() {
        Long userId = 1L;
        ConfirmGifticonPurchaseRequest request = request(10L, 2, 2000, "request-1");
        GifticonPurchase existing = existingPurchase(10L, 2, 2000, "수신자", "01012345678", "메시지");
        LocalDateTime requestedAt = LocalDateTime.now();

        when(existing.getId()).thenReturn(100L);
        when(existing.getRequestedAt()).thenReturn(requestedAt);
        stubExisting(userId, request.clientRequestId(), existing);

        var response = service.confirm(userId, request);

        assertEquals(100L, response.purchaseId());
        assertEquals(requestedAt, response.requestedAt());
        verify(purchaseRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(pointService, never()).spendPoint(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()
        );
        verifyNoInteractions(userReportPenaltyService);
    }

    @Test
    void sameIdempotencyKeyWithDifferentRequestReturnsDuplicateRequest() {
        Long userId = 1L;
        ConfirmGifticonPurchaseRequest request = new ConfirmGifticonPurchaseRequest(
                10L,
                2,
                2000,
                "request-1",
                "수신자",
                "01012345678",
                "다른 메시지"
        );
        GifticonPurchase existing = existingPurchase(10L, 2, 2000, "수신자", "01012345678", "메시지");
        stubExisting(userId, request.clientRequestId(), existing);

        CustomException exception = assertThrows(CustomException.class, () -> service.confirm(userId, request));

        assertSame(GifticonErrorCode.DUPLICATE_REQUEST, exception.getErrorCode());
        verify(purchaseRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(pointService, never()).spendPoint(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void withdrawnUserIsRejectedAfterThePurchaseLock() {
        Long userId = 1L;
        Users withdrawn = mock(Users.class);
        when(withdrawn.getStatus()).thenReturn(UserStatus.WITHDRAWN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(withdrawn));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.confirm(userId, request(10L, 1, 1000, "request-withdrawn"))
        );

        assertSame(AuthErrorCode.USER_WITHDRAWN, exception.getErrorCode());
        verify(userRepository).lockUserRow(userId);
        verify(purchaseRepository).findByUser_UserIdAndClientRequestId(userId, "request-withdrawn");
        verifyNoInteractions(productRepository, pointService, userReportPenaltyService);
    }

    @Test
    void currentReportRestrictionBlocksANewPurchaseAfterThePurchaseLock() {
        Long userId = 1L;
        Users active = mock(Users.class);
        when(active.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(active));
        when(userReportPenaltyService.hasActiveRestriction(userId, UserStatus.ACTIVE)).thenReturn(true);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.confirm(userId, request(10L, 1, 1000, "request-suspended"))
        );

        assertSame(AuthErrorCode.USER_SUSPENDED, exception.getErrorCode());
        verify(userRepository).lockUserRow(userId);
        verify(purchaseRepository).findByUser_UserIdAndClientRequestId(userId, "request-suspended");
        verifyNoInteractions(productRepository, pointService);
    }

    private void stubExisting(Long userId, String requestId, GifticonPurchase existing) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(mock(Users.class)));
        when(purchaseRepository.findByUser_UserIdAndClientRequestId(userId, requestId))
                .thenReturn(Optional.of(existing));
    }

    private GifticonPurchase existingPurchase(
            Long productId,
            int quantity,
            int totalPoints,
            String recipientName,
            String recipientPhone,
            String giftMessage
    ) {
        GifticonProduct product = mock(GifticonProduct.class);
        GifticonPurchase purchase = mock(GifticonPurchase.class);
        when(product.getId()).thenReturn(productId);
        when(purchase.getProduct()).thenReturn(product);
        when(purchase.getQuantity()).thenReturn(quantity);
        when(purchase.getTotalPricePoints()).thenReturn(totalPoints);
        when(purchase.getRecipientName()).thenReturn(recipientName);
        when(purchase.getRecipientPhone()).thenReturn(recipientPhone);
        when(purchase.getGiftMessage()).thenReturn(giftMessage);
        return purchase;
    }

    private ConfirmGifticonPurchaseRequest request(Long productId, int quantity, int points, String requestId) {
        return new ConfirmGifticonPurchaseRequest(
                productId,
                quantity,
                points,
                requestId,
                "수신자",
                "01012345678",
                "메시지"
        );
    }
}
