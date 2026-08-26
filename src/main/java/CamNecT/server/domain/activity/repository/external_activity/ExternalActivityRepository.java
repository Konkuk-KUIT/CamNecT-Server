package CamNecT.server.domain.activity.repository.external_activity;

import CamNecT.server.domain.activity.model.external_activity.ExternalActivity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExternalActivityRepository extends JpaRepository<ExternalActivity, Long>, ExternalActivityRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from ExternalActivity e where e.activityId = :id")
    Optional<ExternalActivity> findByIdForRead(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExternalActivity e where e.activityId = :id")
    Optional<ExternalActivity> findByIdForUpdate(@Param("id") Long id);

    @Query("select e.title from ExternalActivity e where e.activityId = :id")
    Optional<String> findTitleByActivityId(@Param("id") Long id);

}
