package CamNecT.server.domain.profile.components.institutions.dto;

import CamNecT.server.domain.profile.components.institutions.model.Campus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampusResponse {

    private Long campusId;
    private String campusName;
    private String fullCampusName;
    private String campusRelation;
    private Integer campusOrder;
    private String region;

    public static CampusResponse from(Campus campus) {
        return CampusResponse.builder()
                .campusId(campus.getCampusId())
                .campusName(campus.getCampusName())
                .fullCampusName(campus.getFullCampusName())
                .campusRelation(campus.getCampusRelation())
                .campusOrder(campus.getCampusOrder())
                .region(campus.getRegion())
                .build();
    }
}
