package CamNecT.server.domain.profile.components.institutions.service;

import CamNecT.server.domain.profile.components.institutions.dto.InstitutionListResponse;
import CamNecT.server.domain.profile.components.institutions.dto.InstitutionResponse;
import CamNecT.server.domain.profile.components.institutions.model.Campus;
import CamNecT.server.domain.profile.components.institutions.model.Institutions;
import CamNecT.server.domain.profile.components.institutions.repository.CampusRepository;
import CamNecT.server.domain.profile.components.institutions.repository.InstitutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionServiceTest {

    @Mock InstitutionRepository institutionRepository;
    @Mock CampusRepository campusRepository;

    @InjectMocks InstitutionService institutionService;

    @Test
    void searchInstitutionsIncludesCampusIdsUsingOneBatchLookup() {
        Institutions konkuk = institution(1L, "KU", "건국대학교");
        Institutions hanyang = institution(2L, "HYU", "한양대학교");
        Campus seoul = campus(18L, konkuk, "서울캠퍼스", 1);
        Campus glocal = campus(17L, konkuk, "GLOCAL캠퍼스", 2);
        Campus hanyangSeoul = campus(21L, hanyang, "서울캠퍼스", 1);

        when(institutionRepository.searchActiveInstitutions(eq("대학"), any(Pageable.class)))
                .thenReturn(List.of(konkuk, hanyang));
        when(campusRepository.findActiveByInstitutionIds(List.of(1L, 2L)))
                .thenReturn(List.of(seoul, glocal, hanyangSeoul));

        InstitutionListResponse result = institutionService.searchInstitutions("대학");

        assertThat(result.getInstitutions()).hasSize(2);
        assertThat(result.getInstitutions().get(0).getCampuses())
                .extracting("campusId", "campusName", "campusOrder")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(18L, "서울캠퍼스", 1),
                        org.assertj.core.groups.Tuple.tuple(17L, "GLOCAL캠퍼스", 2)
                );
        assertThat(result.getInstitutions().get(1).getCampuses())
                .extracting("campusId")
                .containsExactly(21L);
        verify(campusRepository).findActiveByInstitutionIds(List.of(1L, 2L));
    }

    @Test
    void getInstitutionIncludesItsActiveCampuses() {
        Institutions konkuk = institution(1L, "KU", "건국대학교");
        Campus seoul = campus(18L, konkuk, "서울캠퍼스", 1);
        Campus glocal = campus(17L, konkuk, "GLOCAL캠퍼스", 2);

        when(institutionRepository.findById(1L)).thenReturn(Optional.of(konkuk));
        when(campusRepository.findByInstitution_InstitutionIdAndIsActiveTrueOrderByCampusOrderAsc(1L))
                .thenReturn(List.of(seoul, glocal));

        InstitutionResponse result = institutionService.getInstitution(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCampuses())
                .extracting("campusId", "campusName")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(18L, "서울캠퍼스"),
                        org.assertj.core.groups.Tuple.tuple(17L, "GLOCAL캠퍼스")
                );
    }

    @Test
    void emptySearchDoesNotQueryCampuses() {
        when(institutionRepository.searchActiveInstitutions(eq("없는학교"), any(Pageable.class)))
                .thenReturn(List.of());

        InstitutionListResponse result = institutionService.searchInstitutions("없는학교");

        assertThat(result.getInstitutions()).isEmpty();
        verify(campusRepository, never()).findActiveByInstitutionIds(any());
    }

    private Institutions institution(Long id, String code, String name) {
        return Institutions.builder()
                .institutionId(id)
                .institutionCode(code)
                .institutionNameKor(name)
                .build();
    }

    private Campus campus(Long id, Institutions institution, String name, int order) {
        return Campus.builder()
                .campusId(id)
                .institution(institution)
                .campusName(name)
                .fullCampusName(institution.getInstitutionNameKor() + " " + name)
                .campusRelation(order == 1 ? "본교" : "복수캠퍼스")
                .campusOrder(order)
                .region("서울특별시")
                .build();
    }
}
