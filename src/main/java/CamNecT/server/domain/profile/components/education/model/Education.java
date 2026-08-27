package CamNecT.server.domain.profile.components.education.model;

import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.profile.components.institutions.model.Campus;
import CamNecT.server.domain.profile.components.institutions.model.Institutions;
import CamNecT.server.domain.profile.components.majors.model.Majors;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

// 학력
@Entity
@Table(name = "Education")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Education {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "education_id")
    private Long educationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institutions institution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_id", nullable = false)
    private Campus campus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "major_id")
    private Majors major;

    @Column(name = "degree", length = 50)
    private String degree;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EducationStatus status;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    public void updateEducation(Institutions institution, Campus campus,
                                LocalDate startDate, LocalDate endDate,
                                EducationStatus status, String description) {
        this.institution = institution;
        this.campus = campus;
//        this.major = major;
//        this.degree = degree;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.description = description;
    }
}