package CamNecT.server.global.point.repository;

import CamNecT.server.global.point.model.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
}
