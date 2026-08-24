package CamNecT.server.global.notification.service;

import CamNecT.server.global.notification.dto.request.AdminAnnouncementRequest;
import CamNecT.server.global.notification.event.AdminAnnouncementNotifiableEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAnnouncementBatchService {

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public long dispatch(Long adminUserId, AdminAnnouncementRequest request, List<Long> receiverIds) {
        String link = normalize(request.link());

        for (Long receiverId : receiverIds) {
            eventPublisher.publishEvent(
                    new AdminAnnouncementNotifiableEvent(
                            receiverId,
                            null, // 시스템 알림으로 보이게
                            request.message(),
                            link
                    )
            );
        }

        return receiverIds.size();
    }

    private String normalize(String link) {
        return (link == null || link.isBlank()) ? null : link.trim();
    }
}
