package CamNecT.server.domain.profile.components.institutions.dto;

import CamNecT.server.domain.profile.components.institutions.model.Campus;
import CamNecT.server.domain.profile.components.institutions.model.Institutions;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class InstitutionResponse {

    private Long id;
    private String code;
    private String nameKor;
    private String nameEng;
    private List<CampusResponse> campuses;

    public static InstitutionResponse from(Institutions institution) {
        return from(institution, List.of());
    }

    public static InstitutionResponse from(Institutions institution, List<Campus> campuses) {
        return InstitutionResponse.builder()
                .id(institution.getInstitutionId())
                .code(institution.getInstitutionCode())
                .nameKor(institution.getInstitutionNameKor())
                .nameEng(institution.getInstitutionNameEng())
                .campuses(campuses.stream()
                        .map(CampusResponse::from)
                        .toList())
                .build();
    }
}
