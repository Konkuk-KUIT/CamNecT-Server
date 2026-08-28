package CamNecT.server.domain.profile.components.archive.service.util;

import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.service.PostSummaryAssembler;
import CamNecT.server.domain.profile.components.archive.dto.response.MyArchiveResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityArchiveAssembler {

    private final PostSummaryAssembler postSummaryAssembler;

    public CommunityAssembleResult assemble(Long userId, boolean adminRead, List<Posts> posts) {

        if (posts == null || posts.isEmpty()) {
            return new CommunityAssembleResult(List.of(), null);
        }

        var res = postSummaryAssembler.assemble(userId, adminRead, posts);

        List<MyArchiveResponse.Item> items = res.items().stream()
                .map(MyArchiveResponse.CommunityItem::from)
                .map(i -> (MyArchiveResponse.Item) i)
                .toList();

        Long nextHotScore = res.cursorStats().hotScore();

        return new CommunityAssembleResult(items, nextHotScore);
    }

    public record CommunityAssembleResult(
            List<MyArchiveResponse.Item> items,
            Long nextHotScore
    ) {}
}
