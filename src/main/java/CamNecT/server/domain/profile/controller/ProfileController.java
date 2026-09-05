package CamNecT.server.domain.profile.controller;

import CamNecT.server.domain.auth.service.PasswordService;
import CamNecT.server.domain.profile.dto.request.UpdateBioRequest;
import CamNecT.server.domain.profile.dto.request.UpdatePasswordRequest;
import CamNecT.server.domain.profile.dto.request.UpdatePrivacyRequest;
import CamNecT.server.domain.profile.dto.request.UpdateProfileImageRequest;
import CamNecT.server.domain.profile.dto.request.UpdateProfileTagsRequest;
import CamNecT.server.domain.profile.dto.response.ProfileResponse;
import CamNecT.server.domain.profile.dto.response.ProfileSettingsResponse;
import CamNecT.server.domain.profile.dto.response.ProfileStatusResponse;
import CamNecT.server.domain.profile.service.ProfileService;
import CamNecT.server.global.common.auth.UserId;
import CamNecT.server.global.common.response.ApiResponse;
import CamNecT.server.global.common.response.ErrorResponse;
import CamNecT.server.global.storage.dto.request.PresignUploadRequest;
import CamNecT.server.global.storage.dto.response.PresignUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Profile", description = "프로필 관련 API")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final PasswordService passwordService;

    @Operation(summary = "유저 프로필 조회", description = "특정 사용자의 ID를 통해 프로필 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 잘못된 profileUserId 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44401 사용자 / 44402 사용자 프로필을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 프로필 구성 데이터 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{profileUserId}")
    public ApiResponse<ProfileResponse> getUserProfile(
            @UserId Long loginUserId,
            @PathVariable Long profileUserId
    ) {
        return ApiResponse.success(profileService.getUserProfile(loginUserId, profileUserId));
    }

    @Operation(summary = "프로필 이미지 업로드 URL 생성", description = "프로필 이미지를 업로드하기 위한 S3 presigned URL을 생성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청값 검증 실패 / 49001 파일 크기가 0 이하", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자 / 41303 탈퇴한 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "49005 프로필 이미지 용량 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type / 49004 허용되지 않은 이미지 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "49006 미사용 프로필 이미지 업로드 티켓 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 Presigned URL 발급 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/uploads/presign")
    public ApiResponse<PresignUploadResponse> presignProfileImageUpload(
            @UserId Long userId,
            @RequestBody @Valid PresignUploadRequest req
    ) {
        return ApiResponse.success(profileService.presignProfileImageUpload(userId, req));
    }

    @Operation(summary = "프로필 태그 수정", description = "사용자의 프로필 태그를 활성 상태의 태그 ID 목록으로 교체합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청값 검증 실패 / 44030 존재하지 않거나 비활성화된 태그 포함", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자 / 41303 탈퇴한 사용자", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 태그 갱신 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/tags")
    public ApiResponse<ProfileStatusResponse> updateProfileTags(
            @UserId Long userId,
            @RequestBody @Valid UpdateProfileTagsRequest req
    ) {
        return ApiResponse.success(profileService.updateProfileTags(userId, req));
    }

    @Operation(summary = "마이페이지 조회", description = "로그인한 사용자의 프로필 전체 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44402 사용자 프로필을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 마이페이지 구성 데이터 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public ApiResponse<ProfileResponse> getMyProfile(@UserId Long loginUserId) {
        return ApiResponse.success(profileService.getUserProfile(loginUserId, loginUserId));
    }

    @Operation(summary = "내 프로필 이미지 수정", description = "업로드 티켓의 이미지로 프로필 사진을 교체합니다. profileImageKey가 비어 있으면 기존 이미지를 제거합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청 형식 오류 / 49010 만료·사용된 티켓 / 49011 업로드 객체와 티켓 정보 불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "41302 정지된 사용자 / 41303 탈퇴한 사용자 / 49310 티켓 소유자·목적 불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44402 사용자 프로필 / 49410 업로드 티켓 / 49401 저장 이미지를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "49902 업로드 이미지 확인 실패 / 49904 이미지 이동 실패 / 50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/me/image")
    public ApiResponse<Void> updateMyProfileImage(
            @UserId Long userId,
            @RequestBody @Valid UpdateProfileImageRequest req
    ) {
        profileService.updateMyProfileImage(userId, req);
        return ApiResponse.success(null);
    }

    @Operation(summary = "마이페이지 한줄 소개 수정", description = "자신의 프로필 한줄 소개를 100자 이내로 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청값 검증 실패 또는 100자 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44402 사용자 프로필을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 소개 수정 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/me/bio")
    public ApiResponse<ProfileStatusResponse> updateMyBio(
            @UserId Long userId,
            @RequestBody @Valid UpdateBioRequest req
    ) {
        return ApiResponse.success(profileService.updateBio(userId, req.bio()));
    }

    @Operation(summary = "마이페이지 개인정보 노출 여부 수정", description = "팔로워 수, 학력, 경력, 자격증 공개 여부 중 전달된 항목을 변경합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청 본문 형식 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44402 사용자 프로필을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 공개 설정 수정 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/me/privacy")
    public ApiResponse<Void> updatePrivacy(
            @UserId Long userId,
            @RequestBody @Valid UpdatePrivacyRequest request
    ) {
        profileService.updatePrivacy(userId, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "마이페이지 환경설정 조회", description = "본인의 이름, 프로필 사진, 이메일을 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "44402 사용자 프로필을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 환경설정 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me/settings")
    public ApiResponse<ProfileSettingsResponse> getMySettings(@UserId Long userId) {
        return ApiResponse.success(profileService.getMySettings(userId));
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "비밀번호 변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "40000 요청값 검증 실패 / 41010 새 비밀번호 형식 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41101 현재 비밀번호 불일치 / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "50000 비밀번호 변경 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePassword(@UserId Long userId, @RequestBody @Valid UpdatePasswordRequest req) {
        passwordService.updateMyPassword(userId, req);
    }
}
