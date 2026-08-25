package CamNecT.server.domain.profile.components.certificate.service;

import CamNecT.server.domain.profile.components.ProfileComponentAccessGuard;
import CamNecT.server.domain.profile.components.certificate.dto.request.CertificateRequest;
import CamNecT.server.domain.profile.components.certificate.model.Certificate;
import CamNecT.server.domain.profile.components.certificate.repository.CertificateRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceLockingTest {

    @Mock CertificateRepository certificateRepository;
    @Mock ProfileComponentAccessGuard accessGuard;

    @InjectMocks CertificateService service;

    @Test
    void everyMutationUsesTheSameUserLockAsWithdrawal() {
        Users owner = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        Certificate certificate = Certificate.builder()
                .certificateId(10L)
                .user(owner)
                .certificateName("자격증")
                .acquiredDate(LocalDate.of(2025, 1, 1))
                .build();
        CertificateRequest request = new CertificateRequest(
                "자격증", LocalDate.of(2025, 1, 1), null);
        when(accessGuard.requireAuthenticatedUserForUpdate(1L)).thenReturn(owner);
        when(certificateRepository.findById(10L)).thenReturn(Optional.of(certificate));

        service.addCertificate(1L, request);
        service.updateCertificate(1L, 10L, request);
        service.deleteCertificate(1L, 10L);

        verify(accessGuard, times(3)).requireAuthenticatedUserForUpdate(1L);
    }
}
