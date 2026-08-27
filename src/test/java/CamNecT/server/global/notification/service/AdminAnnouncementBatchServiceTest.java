package CamNecT.server.global.notification.service;

import CamNecT.server.global.notification.dto.request.AdminAnnouncementRequest;
import CamNecT.server.global.notification.event.AdminAnnouncementNotifiableEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static CamNecT.server.global.notification.dto.request.AdminAnnouncementRequest.TargetType.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminAnnouncementBatchServiceTest {

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AdminAnnouncementBatchService service = new AdminAnnouncementBatchService(eventPublisher);

    @Test
    void dispatchTrimsLinkBeforePublishingEvent() {
        AdminAnnouncementRequest request = new AdminAnnouncementRequest(
                "message",
                "  /events/1  ",
                USERS,
                List.of(2L)
        );

        service.dispatch(1L, request, List.of(2L));

        ArgumentCaptor<AdminAnnouncementNotifiableEvent> captor =
                ArgumentCaptor.forClass(AdminAnnouncementNotifiableEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().link()).isEqualTo("/events/1");
    }
}
