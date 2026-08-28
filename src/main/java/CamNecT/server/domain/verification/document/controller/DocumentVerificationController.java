package CamNecT.server.domain.verification.document.controller;

import CamNecT.server.domain.verification.document.dto.DocumentVerificationDetailResponse;
import CamNecT.server.domain.verification.document.dto.DocumentVerificationListItemResponse;
import CamNecT.server.domain.verification.document.dto.SubmitDocumentVerificationRequest;
import CamNecT.server.domain.verification.document.dto.SubmitDocumentVerificationResponse;
import CamNecT.server.domain.verification.document.service.DocumentVerificationService;
import CamNecT.server.global.common.auth.UserId;
import CamNecT.server.global.common.response.ErrorResponse;
import CamNecT.server.global.storage.dto.request.PresignUploadRequest;
import CamNecT.server.global.storage.dto.response.PresignDownloadResponse;
import CamNecT.server.global.storage.dto.response.PresignUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Document Verification", description = "사용자: 문서 인증 업로드/제출/조회/취소")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/verification/documents")
public class DocumentVerificationController {

    private final DocumentVerificationService service;

    @Operation(
            summary = "업로드용 presigned URL 발급",
            description = "클라이언트가 S3 등에 업로드할 수 있도록 presigned URL을 발급합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "발급 성공",
                    content = @Content(schema = @Schema(implementation = PresignUploadResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "40000 요청값 검증 실패 / 42022 파일 크기가 0 이하", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "42123 문서 파일 용량 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type / 42124 허용되지 않은 문서 Content-Type", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "42931 승인 대기 상태가 아니어서 문서 업로드 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "49006 미사용 업로드 티켓 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "50000 Presigned URL 발급 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/uploads/presign")
    public PresignUploadResponse presignUpload(
            @UserId Long userId,
            @RequestBody @Valid PresignUploadRequest req
    ) {
        return service.presignUpload(userId, req);
    }

    @Operation(
            summary = "문서 인증 제출",
            description = "업로드된 문서 키(documentKey)를 기반으로 인증 제출을 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "제출 성공",
                    content = @Content(schema = @Schema(implementation = SubmitDocumentVerificationResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "40000 요청값 검증 실패 / 42020 문서 키 누락 / 49010 만료·사용된 티켓 / 49011 업로드 파일 불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "49310 티켓 소유자·목적 또는 사용 권한 불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "49410 업로드 티켓 / 49401 업로드된 저장 파일을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "42930 이미 처리 대기 중인 제출이 존재함 / 42931 승인 대기 상태가 아니어서 제출 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "41500 지원하지 않는 요청 Content-Type / 42124 문서 Content-Type 누락 또는 불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "49902 업로드 파일 확인 실패 / 49904 파일 이동 실패 / 50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitDocumentVerificationResponse submit(
            @UserId Long userId,
            @RequestBody @Valid SubmitDocumentVerificationRequest req
    ) {
        return service.submit(userId, req.docType(), req.documentKey());
    }

    @Operation(
            summary = "내 문서 인증 제출 목록 조회",
            description = "현재 로그인한 사용자의 제출 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DocumentVerificationListItemResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "50000 제출 목록 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public List<DocumentVerificationListItemResponse> mySubmissions(@UserId Long userId) {
        return service.mySubmissions(userId);
    }

    @Operation(
            summary = "내 문서 인증 제출 상세 조회",
            description = "submissionId 기준으로 내 제출 상세를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DocumentVerificationDetailResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "40000 잘못된 submissionId 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "42401 제출이 없거나 현재 사용자의 제출이 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "50000 제출 상세 조회 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{submissionId}")
    public DocumentVerificationDetailResponse mySubmissionDetail(
            @UserId Long userId,
            @PathVariable Long submissionId
    ) {
        return service.mySubmissionDetail(userId, submissionId);
    }

    @Operation(
            summary = "내 제출 파일 다운로드 URL 발급",
            description = "특정 제출 건의 파일을 다운로드할 수 있는 presigned URL을 발급합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "발급 성공",
                    content = @Content(schema = @Schema(implementation = PresignDownloadResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "40000 잘못된 submissionId 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "42401 제출 / 42403 제출 파일 / 49401 저장 파일을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "49902 저장 파일 확인 실패 / 50000 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{submissionId}/download-url")
    public PresignDownloadResponse myDownloadUrl(
            @UserId Long userId,
            @PathVariable Long submissionId
    ) {
        return service.myDownloadUrl(userId, submissionId);
    }

    @Operation(
            summary = "문서 인증 제출 취소",
            description = "submissionId 기준으로 제출을 취소합니다. 성공 시 204를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "취소 성공"),
            @ApiResponse(responseCode = "400", description = "40000 잘못된 submissionId 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "40100 유효하지 않거나 만료된 JWT / 41103 인증 헤더 누락·형식 오류 / 41104 토큰 타입 누락 / 41106 허용되지 않은 토큰 타입", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "42401 제출이 없거나 현재 사용자의 제출이 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "42910 PENDING 상태가 아니어서 취소할 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "50000 취소 처리 또는 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{submissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @UserId Long userId,
            @PathVariable Long submissionId
    ) {
        service.cancel(userId, submissionId);
    }
}
