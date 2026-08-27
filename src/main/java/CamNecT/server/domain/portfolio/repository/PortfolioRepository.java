package CamNecT.server.domain.portfolio.repository;

import CamNecT.server.domain.portfolio.dto.response.PortfolioPreviewResponse;
import CamNecT.server.domain.portfolio.model.PortfolioProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

@Repository
public interface PortfolioRepository extends JpaRepository<PortfolioProject, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PortfolioProject p where p.portfolioId = :portfolioId")
    Optional<PortfolioProject> findByIdForUpdate(@Param("portfolioId") Long portfolioId);

    @Query("SELECT new CamNecT.server.domain.portfolio.dto.response.PortfolioPreviewResponse(p.portfolioId, p.title, p.subtitle, p.thumbnailUrl, p.isPublic, p.isFavorite, p.updatedAt) " +
            "FROM PortfolioProject p " +
            "WHERE p.userId = :userId " +
            "ORDER BY p.createdAt DESC")
    List<PortfolioPreviewResponse> findPreviewsByUserId(@Param("userId") Long userId);

    @Query("""
    SELECT new CamNecT.server.domain.portfolio.dto.response.PortfolioPreviewResponse(p.portfolioId, p.title, p.subtitle, p.thumbnailUrl, p.isPublic, p.isFavorite, p.updatedAt)
    FROM PortfolioProject p
    WHERE p.userId = :userId AND p.isPublic = true
    ORDER BY p.createdAt DESC
    """)
    List<PortfolioPreviewResponse> findPublicPreviewsByUserId(@Param("userId") Long userId);

}
