package CamNecT.server.domain.profile.components.institutions.dto;

import CamNecT.server.domain.profile.components.institutions.model.Campus;
import CamNecT.server.domain.profile.components.institutions.model.Institutions;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
public class InstitutionListResponse {
    private List<InstitutionResponse> institutions;

    public static InstitutionListResponse from(
            List<Institutions> institutions,
            Map<Long, List<Campus>> campusesByInstitution
    ) {
        return InstitutionListResponse.builder()
                .institutions(institutions.stream()
                        .map(institution -> InstitutionResponse.from(
                                institution,
                                campusesByInstitution.getOrDefault(
                                        institution.getInstitutionId(),
                                        Collections.emptyList()
                                )
                        ))
                        .collect(Collectors.toList()))
                .build();
    }

    public static InstitutionListResponse empty() {
        return InstitutionListResponse.builder()
                .institutions(Collections.emptyList())
                .build();
    }
}
