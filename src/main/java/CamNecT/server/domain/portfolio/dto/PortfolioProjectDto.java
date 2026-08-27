package CamNecT.server.domain.portfolio.dto;


import CamNecT.server.domain.portfolio.model.PortfolioProject;

import java.time.LocalDate;
import java.util.List;

public record PortfolioProjectDto(
        Long portfolioId,
        Long userId,
        String title,
        String subtitle,
        String thumbnailUrl, // CDN URL이 담길 곳
        LocalDate startDate,
        LocalDate endDate,
        String description,
        boolean isPublic,
        boolean isFavorite,
        List<String> assignedRole,
        List<String> techStack,
        String review,
        LocalDate createdAt,
        LocalDate updatedAt
) {
    // 엔티티를 DTO로 변환하는 정적 팩토리 메서드 (서비스 로직 깔끔화)
    public static PortfolioProjectDto from(PortfolioProject p, String fullThumbnailUrl) {
        return new PortfolioProjectDto(
                p.getPortfolioId(),
                p.getUserId(),
                p.getTitle(),
                p.getSubtitle(),
                fullThumbnailUrl,
                p.getStartDate(),
                p.getEndDate(),
                p.getDescription(),
                p.isPublic(),
                p.isFavorite(),
                p.getAssignedRole(),
                p.getTechStack(),
                p.getReview(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}