package CamNecT.server.domain.profile.components.certificate.service;

import CamNecT.server.domain.profile.components.ProfileComponentAccessGuard;
import CamNecT.server.domain.profile.components.certificate.dto.request.CertificateRequest;
import CamNecT.server.domain.profile.components.certificate.dto.response.CertificateResponse;
import CamNecT.server.domain.profile.components.certificate.model.Certificate;
import CamNecT.server.domain.profile.components.certificate.repository.CertificateRepository;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final ProfileComponentAccessGuard accessGuard;

    @Transactional
    public void addCertificate(Long userId, CertificateRequest request) {
        Users user = accessGuard.requireAuthenticatedUserForUpdate(userId);

        Certificate certificate = Certificate.builder()
                .user(user)
                .certificateName(request.certificateName())
                .acquiredDate(request.acquiredDate())
                .credentialUrl(request.credentialUrl())
//                .issuerName(request.issuerName())
//                .expireDate(request.expireDate())
//                .description(request.description())
                .build();

        certificateRepository.save(certificate);
    }

    public List<CertificateResponse> getMyCertificate(Long userId) {
        accessGuard.requireAuthenticatedUser(userId);
        return certificateRepository.findAllByUser_UserIdOrderByAcquiredDateDesc(userId)
                .stream()
                .map(CertificateResponse::from)
                .toList();
    }

    @Transactional
    public void updateCertificate(Long userId, Long certificateId, CertificateRequest request) {
        accessGuard.requireAuthenticatedUserForUpdate(userId);
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new CustomException(UserErrorCode.CERTIFICATE_NOT_FOUND));

        // 본인 확인
        if (!certificate.getUser().getUserId().equals(userId)) {
            throw new CustomException(UserErrorCode.CERTIFICATE_FORBIDDEN);
        }

        certificate.updateCertificate(
                request.certificateName(),
                request.acquiredDate(),
                request.credentialUrl()
//                request.issuerName(),
//                request.expireDate(),
//                request.description()
        );
    }

    @Transactional
    public void deleteCertificate(Long userId, Long certificateId) {
        accessGuard.requireAuthenticatedUserForUpdate(userId);
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new CustomException(UserErrorCode.CERTIFICATE_NOT_FOUND));

        if (!certificate.getUser().getUserId().equals(userId)) {
            throw new CustomException(UserErrorCode.CERTIFICATE_FORBIDDEN);
        }

        certificateRepository.delete(certificate);
    }
}
