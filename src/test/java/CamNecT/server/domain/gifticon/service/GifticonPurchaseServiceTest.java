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
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private ValidatorFactory validatorFactory;
    private GifticonPurchaseService service;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        service = new GifticonPurchaseService(productRepository, purchaseRepository, userRepository,
                pointService, userReportPenaltyService, new GifticonEmailPolicy(validatorFactory.getValidator()));
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void sameIdempotencyKeyAndSameRequestReturnsExistingPurchase() {
        Long userId = 1L;
        ConfirmGifticonPurchaseRequest request = request(10L, 2, 2000, "request-1");
        GifticonPurchase existing = existingPurchase(10L, 2, 2000, "수신자", "recipient@example.com", "메시지");
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
                "recipient@example.com",
                "다른 메시지"
        );
        GifticonPurchase existing = existingPurchase(10L, 2, 2000, "수신자", "recipient@example.com", "메시지");
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
            String recipientEmail,
            String giftMessage
    ) {
        GifticonProduct product = mock(GifticonProduct.class);
        GifticonPurchase purchase = mock(GifticonPurchase.class);
        when(product.getId()).thenReturn(productId);
        when(purchase.getProduct()).thenReturn(product);
        when(purchase.getQuantity()).thenReturn(quantity);
        when(purchase.getTotalPricePoints()).thenReturn(totalPoints);
        when(purchase.getRecipientName()).thenReturn(recipientName);
        when(purchase.getRecipientEmail()).thenReturn(recipientEmail);
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
                "recipient@example.com",
                "메시지"
        );
    }

    @Test
    void newPurchaseUsesSignupEmailWhenRecipientIsOmitted() {
        stubNewPurchase("buyer@example.com");

        service.confirm(1L, new ConfirmGifticonPurchaseRequest(10L, 2, 2000, "self", null, null, null));

        ArgumentCaptor<GifticonPurchase> saved = ArgumentCaptor.forClass(GifticonPurchase.class);
        verify(purchaseRepository).saveAndFlush(saved.capture());
        assertEquals("buyer@example.com", saved.getValue().getBuyerEmail());
        assertEquals("buyer@example.com", saved.getValue().getRecipientEmail());
        verify(pointService).spendPoint(eq(1L), eq(2000), any());
    }

    @Test
    void giftPurchaseSnapshotsRecipientEmailSeparatelyFromBuyer() {
        stubNewPurchase("buyer@example.com");

        service.confirm(1L, new ConfirmGifticonPurchaseRequest(10L, 2, 2000, "gift",
                "수신자", " recipient@example.com ", "메시지"));

        ArgumentCaptor<GifticonPurchase> saved = ArgumentCaptor.forClass(GifticonPurchase.class);
        verify(purchaseRepository).saveAndFlush(saved.capture());
        assertEquals("buyer@example.com", saved.getValue().getBuyerEmail());
        assertEquals("recipient@example.com", saved.getValue().getRecipientEmail());
    }

    @Test
    void missingOrInvalidSignupEmailDoesNotSaveOrSpendPoints() {
        for (String email : new String[]{null, " ", "invalid-email"}) {
            stubNewPurchase(email);
            CustomException error = assertThrows(CustomException.class, () -> service.confirm(1L,
                    new ConfirmGifticonPurchaseRequest(10L, 2, 2000, "self", null, null, null)));
            assertSame(GifticonErrorCode.INVALID_RECIPIENT_EMAIL, error.getErrorCode());
        }
        verify(purchaseRepository, never()).saveAndFlush(any());
        verify(pointService, never()).spendPoint(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void retryUsesOriginalBuyerEmailEvenIfAccountEmailChanged() {
        Users user = Users.builder().userId(1L).email("changed@example.com").build();
        GifticonPurchase existing = GifticonPurchase.builder().id(100L)
                .product(GifticonProduct.builder().id(10L).build())
                .quantity(2).totalPricePoints(2000)
                .buyerEmail("original@example.com").recipientEmail("original@example.com")
                .requestedAt(LocalDateTime.now()).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findByUser_UserIdAndClientRequestId(1L, "self"))
                .thenReturn(Optional.of(existing));

        var response = service.confirm(1L,
                new ConfirmGifticonPurchaseRequest(10L, 2, 2000, "self", null, null, null));

        assertEquals(100L, response.purchaseId());
        verify(purchaseRepository, never()).saveAndFlush(any());
        verify(pointService, never()).spendPoint(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void sameKeyWithDifferentRecipientEmailIsRejected() {
        GifticonPurchase existing = GifticonPurchase.builder()
                .product(GifticonProduct.builder().id(10L).build())
                .quantity(2).totalPricePoints(2000).recipientName("수신자")
                .recipientEmail("someone-else@example.com").giftMessage("메시지").build();
        stubExisting(1L, "request-1", existing);

        CustomException error = assertThrows(CustomException.class,
                () -> service.confirm(1L, request(10L, 2, 2000, "request-1")));

        assertSame(GifticonErrorCode.DUPLICATE_REQUEST, error.getErrorCode());
        verify(purchaseRepository, never()).saveAndFlush(any());
        verify(pointService, never()).spendPoint(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    private void stubNewPurchase(String email) {
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                Users.builder().userId(1L).name("구매자").email(email).build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(
                GifticonProduct.builder().id(10L).pricePoints(1000).build()));
    }
}
