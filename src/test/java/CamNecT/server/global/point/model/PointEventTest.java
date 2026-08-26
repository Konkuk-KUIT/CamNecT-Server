package CamNecT.server.global.point.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PointEventTest {

    @Test
    void gifticonPurchaseKeyUsesThePersistedPurchaseIdAndFitsTheDatabaseColumn() {
        PointEvent event = PointEvent.gifticonPurchase(Long.MAX_VALUE, Long.MAX_VALUE);

        assertThat(event.eventKey())
                .isEqualTo("GIFTICON_PURCHASE:" + Long.MAX_VALUE + ":" + Long.MAX_VALUE)
                .hasSizeLessThanOrEqualTo(64);
        assertThat(event.requestId()).isEqualTo(Long.MAX_VALUE);
    }
}
