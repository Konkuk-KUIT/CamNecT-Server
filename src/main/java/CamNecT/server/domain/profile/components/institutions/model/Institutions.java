package CamNecT.server.domain.profile.components.institutions.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Institutions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Institutions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "institution_id")
    private Long institutionId;

    @Column(name = "institution_code", nullable = false, length = 100)
    private String institutionCode;

    @Column(name = "institution_name_kor", nullable = false, length = 100)
    private String institutionNameKor;

    @Column(name = "institution_name_eng", length = 100)
    private String institutionNameEng;

    @Column(name = "university_type", length = 50)
    private String universityType;

    @Column(name = "primary_region", length = 50)
    private String primaryRegion;

    @Column(name = "source_as_of_date")
    private LocalDate sourceAsOfDate;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
