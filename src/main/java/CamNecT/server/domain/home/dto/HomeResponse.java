package CamNecT.server.domain.home.dto;

import CamNecT.server.domain.alumni.dto.AlumniHomeResponse;
import CamNecT.server.domain.alumni.dto.ProfileCardDto;

import java.util.List;

public record HomeResponse(
        UserSection user,
        CoffeeChatSection coffeeChat,
        RecruitmentSection recruitment,
        PointSection point,
        AlumniSection alumni,
        ContestSection contests
) {

    //사용자
    public record UserSection(String displayName) {}

    //커피챗
    public record CoffeeChatSection(
            long pendingCount,
            List<CoffeeChatPreview> latest2
    ) {
        public record CoffeeChatPreview(
                Long requestId,
                Long senderUserId,
                String senderName,
                String majorName,   // nullable
                String studentNo    // nullable (학번은 이걸로만)
        ) {}

        public static CoffeeChatSection empty() {
            return new CoffeeChatSection(0, List.of());
        }
    }

    //팀원모집
    public record RecruitmentSection(
            long pendingCount,
            List<RecruitmentPreview> latest5
    ) {
        public record RecruitmentPreview(
                Long userId,
                String name,
                String majorName,
                ProfileCardDto profile,
                List<String> tagList,
                Long requestId
        ) {}

        public static RecruitmentSection empty() {
            return new RecruitmentSection(0, List.of());
        }
    }

    //포인트
    public record PointSection(int balance) {}

    //동문탐색
    public record AlumniSection(
            List<AlumniHomeResponse> items,
            boolean hasMore
    ) {
        public static AlumniSection empty() {
            return new AlumniSection(List.of(), false);
        }
    }

    //대외활동
    public record ContestSection(
            List<ContestCard> items,
            boolean hasMore
    ) {
        public record ContestCard(
                Long contestId,
                String title,
                String organizer,
                String thumbnailUrl
        ) {}
        public static ContestSection empty() {
            return new ContestSection(List.of(), false);
        }
    }

    /// 나머지는 전부다 api연동
}
