package CamNecT.server.domain.alumni.service;

import CamNecT.server.domain.alumni.dto.AlumniHomeResponse;
import CamNecT.server.domain.alumni.dto.ProfileCardDto;
import CamNecT.server.domain.alumni.dto.response.AlumniPreviewResponse;
import CamNecT.server.domain.alumni.dto.UserProfileDto;
import CamNecT.server.domain.alumni.repository.AlumniRepository;
import CamNecT.server.domain.chat.model.ChatRequest;
import CamNecT.server.domain.chat.model.ChatRoom;
import CamNecT.server.domain.chat.repository.ChatRoomRepository;
import CamNecT.server.domain.home.dto.HomeResponse;
import CamNecT.server.domain.users.model.UserProfile;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserTagMapRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlumniService {

    private static final long LEGACY_TAG_ID = 111L;
    private static final long CANONICAL_TAG_ID = 53L;

    private final UserTagMapRepository userTagMapRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final AlumniRepository alumniRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final PublicUrlIssuer publicUrlIssuer;

    @Transactional(readOnly = true)
    public Slice<AlumniPreviewResponse> searchAlumni(Long userId, String name, List<Long> tagIdList, Pageable pageable) {
        requireAuthenticatedUser(userId);
        List<Long> normalizedTagIds = normalizeTagIds(tagIdList);
        // 1. ID 페이징 조회
        List<Long> targetIds = alumniRepository.findAlumniIdsByConditions(userId, name, normalizedTagIds, pageable);

        boolean hasNext = targetIds.size() > pageable.getPageSize();
        if (hasNext) {
            targetIds = targetIds.subList(0, pageable.getPageSize());
        }

        if (targetIds.isEmpty()) {
            return new SliceImpl<>(List.of(), pageable, false);
        }

        // 2. Profile 조회
        Map<Long, UserProfile> profileMap = userProfileRepository.findAllByUserIdIn(targetIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, p -> p));

        // 3. Tags 조회
        Map<Long, List<String>> tagMap = userTagMapRepository.findTagNamesWithUserIdByUserIdIn(targetIds)
                .stream()
                .collect(Collectors.groupingBy(
                        row -> (Long) row[0],
                        Collectors.mapping(row -> (String) row[1], Collectors.toList())
                ));

        // 4. 현재 페이지 대상의 활성 커피챗 상대를 한 번에 조회
        Set<Long> activeChatTargetIds = Set.copyOf(chatRoomRepository.findActiveChatPartnerIds(
                userId,
                targetIds,
                ChatRoom.RoomStatus.OPEN,
                ChatRequest.RequestStatus.ACCEPTED,
                ChatRequest.RequestType.COFFEE_CHAT
        ));

        // 5. DTO 변환 (정렬 유지)
        List<AlumniPreviewResponse> content = targetIds.stream()
                .map(id -> {
                    UserProfile profile = profileMap.get(id);
                    if (profile == null) return null;

                    String imgUrl = StringUtils.hasText(profile.getProfileImageKey())
                            ? publicUrlIssuer.issuePublicUrl(profile.getProfileImageKey())
                            : null;

                    UserProfileDto profileDto = UserProfileDto.from(profile).withProfileImageUrl(imgUrl);

                    return new AlumniPreviewResponse(
                            id,
                            profile.getUser().getName(),
                            profileDto,
                            tagMap.getOrDefault(id, List.of()),
                            activeChatTargetIds.contains(id)
                    );
                })
                .filter(Objects::nonNull)
                .toList();

        return new SliceImpl<>(content, pageable, hasNext);
    }

    @Transactional(readOnly = true)
    public HomeResponse.AlumniSection getHomePreview(Long myId, int limit) {

        Pageable pageable = PageRequest.of(0, limit);

        // 1) 추천 정렬된 ID 목록
        List<Long> orderedIds = alumniRepository.findAlumniIdsByConditions(myId, null, List.of(), pageable);
        if (orderedIds.isEmpty()) {
            return HomeResponse.AlumniSection.empty();
        }

        boolean hasMore = orderedIds.size() > limit;
        List<Long> topIds = orderedIds.stream().limit(limit).toList();

        // 2) 프로필 + 유저(이름) 한 번에
        Map<Long, UserProfile> profileMap = userProfileRepository.findAllByUserIdInWithUser(topIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, p -> p));

        Map<Long, List<String>> tagMap =
                userTagMapRepository.findTagNamesWithUserIdByUserIdIn(topIds).stream()
                        .collect(Collectors.groupingBy(
                                row -> (Long) row[0],
                                Collectors.mapping(row -> (String) row[1], Collectors.toList())
                        ));


        // 4) 정렬 유지하면서 AlumniHomeResponse 생성
        List<AlumniHomeResponse> items = topIds.stream()
                .map(id -> {
                    UserProfile p = profileMap.get(id);
                    if (p == null || p.getUser() == null) return null;
                    String imgUrl = null;
                    if (StringUtils.hasText(p.getProfileImageKey())) {
                        imgUrl = publicUrlIssuer.issuePublicUrl(p.getProfileImageKey());
                    }

                    ProfileCardDto card = ProfileCardDto.createCard(p,imgUrl); // 홈에서만 우선 적용

                    return new AlumniHomeResponse(
                            id,
                            p.getUser().getName(),
                            card,
                            tagMap.getOrDefault(id, List.of())
                    );
                })
                .filter(Objects::nonNull)
                .toList();

        return new HomeResponse.AlumniSection(items, hasMore);
    }

    private List<Long> normalizeTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return List.of();

        return tagIds.stream()
                .filter(Objects::nonNull)
                .map(id -> id == LEGACY_TAG_ID ? CANONICAL_TAG_ID : id)
                .distinct()
                .toList();
    }

    private void requireAuthenticatedUser(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
    }
}
