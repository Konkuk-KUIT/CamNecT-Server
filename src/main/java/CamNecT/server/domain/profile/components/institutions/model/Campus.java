package CamNecT.server.domain.profile.components.institutions.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "campuses",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_campuses_institution_name",
                        columnNames = {"institution_id", "campus_name"}
                ),
                @UniqueConstraint(
                        name = "uk_campuses_institution_order",
                        columnNames = {"institution_id", "campus_order"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Campus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "campus_id")
    private Long campusId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institutions institution;

    @Column(name = "campus_name", nullable = false, length = 100)
    private String campusName;

    @Column(name = "full_campus_name", nullable = false, length = 150)
    private String fullCampusName;

    @Column(name = "campus_relation", nullable = false, length = 50)
    private String campusRelation;

    @Column(name = "campus_order", nullable = false)
    private Integer campusOrder;

    @Column(name = "region", nullable = false, length = 50)
    private String region;

    @Column(name = "source_as_of_date", nullable = false)
    private LocalDate sourceAsOfDate;

    @Column(name = "source_url", nullable = false, length = 512)
    private String sourceUrl;

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
