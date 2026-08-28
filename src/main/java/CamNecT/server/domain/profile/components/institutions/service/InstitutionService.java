package CamNecT.server.domain.profile.components.institutions.service;

import CamNecT.server.domain.profile.components.institutions.dto.InstitutionListResponse;
import CamNecT.server.domain.profile.components.institutions.dto.InstitutionResponse;
import CamNecT.server.domain.profile.components.institutions.model.Campus;
import CamNecT.server.domain.profile.components.institutions.repository.CampusRepository;
import CamNecT.server.domain.profile.components.institutions.repository.InstitutionRepository;
import CamNecT.server.domain.profile.components.institutions.model.Institutions;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstitutionService {

    private final InstitutionRepository institutionRepository;
    private final CampusRepository campusRepository;

    public InstitutionListResponse searchInstitutions(String keyword) {
        Pageable limit = PageRequest.of(0, 10);
        List<Institutions> institutions = institutionRepository.searchActiveInstitutions(keyword, limit);

        return InstitutionListResponse.from(institutions, findCampusesByInstitution(institutions));
    }

    public InstitutionResponse getInstitution(Long id) {
        Institutions institution = institutionRepository.findById(id)
                .orElseThrow(() -> new CustomException(UserErrorCode.INSTITUTION_NOT_FOUND));

        List<Campus> campuses = campusRepository
                .findByInstitution_InstitutionIdAndIsActiveTrueOrderByCampusOrderAsc(id);

        return InstitutionResponse.from(institution, campuses);
    }

    private Map<Long, List<Campus>> findCampusesByInstitution(List<Institutions> institutions) {
        if (institutions.isEmpty()) {
            return Map.of();
        }

        List<Long> institutionIds = institutions.stream()
                .map(Institutions::getInstitutionId)
                .toList();

        return campusRepository.findActiveByInstitutionIds(institutionIds).stream()
                .collect(Collectors.groupingBy(
                        campus -> campus.getInstitution().getInstitutionId()
                ));
    }
}
