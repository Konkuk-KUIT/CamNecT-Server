# 신고·관리자 조치 API 계약과 후속 작업

이 문서는 신고 객체 단위 집계 도입 이후 프론트엔드 계약과, 이번 PR에서 의도적으로 제외한 후속 작업을 정리한다.

## 핵심 동작

- 동일한 대상 객체에 접수된 신고는 서버가 하나의 `caseId`로 집계한다. 프론트엔드가 별도로 그룹핑하지 않는다.
- 한 사용자는 동일한 대상 객체를 한 번만 신고할 수 있다. 중복 요청은 HTTP 409, 오류 코드 `51901`이다.
- 요청의 `reportedUserId`는 신뢰하지 않고 서버가 실제 객체 작성자를 조회해 일치 여부를 검증한다.
- 관리자는 개별 제출이 아니라 case를 한 번 승인 또는 반려한다. 승인된 case에는 제재가 한 번만 적용된다.
- 관리자가 승인할 때 최종 신고 카테고리를 결정한다. 최초 신고자가 보낸 카테고리는 참고 정보다.
- 관리자 상세 응답에는 대상 작성자와 기존 제재 이력이 포함된다.

## 신고 접수

`POST /api/v1/reports`

```json
{
  "reportedUserId": 12,
  "reportedPostId": 345,
  "postType": "COMMUNITY",
  "reportCategory": "INSULT_DEFAMATION",
  "title": "신고 제목",
  "context": "상세 사유",
  "evidenceImageUrl": "reports/evidence/..."
}
```

`evidenceImageUrl`은 이름과 달리 공개 URL이 아니라 증거 업로드 presign 응답의 `fileKey`를 전달하는 기존 호환 필드다. 증거가 없으면 `null`로 보낸다.

대상별 `reportedPostId` 의미:

| `postType` | 대상 ID |
| --- | --- |
| `COMMUNITY` | 게시글 ID |
| `COMMUNITY_COMMENT` | 댓글 ID |
| `ACTIVITY` | 대외활동 ID |
| `ACTIVITY_RECRUITMENT` | 모집 ID |
| `USER` | `null` 허용, `reportedUserId`가 대상 |
| `CHAT` | 채팅방 ID |

## 증거 파일

업로드 URL은 기존 `POST /api/v1/reports/uploads/presign/evidence`를 사용한다.

관리자 case 상세의 각 `submissions[]`에는 공개 URL 대신 `hasEvidence`가 내려간다. `true`인 제출의 파일이 필요할 때만 다음 API를 호출한다.

`GET /api/v1/reports/admin/{caseId}/submissions/{reportId}/evidence/download-url`

```json
{
  "success": true,
  "data": {
    "downloadUrl": "https://...",
    "expiresAt": "2026-07-28T23:30:00",
    "fileKey": "reports/evidence/..."
  }
}
```

URL은 짧게 유효한 presigned download URL이다. 만료 후 다시 발급해야 하며 저장하거나 CDN URL로 변환하지 않는다. 첨부가 없으면 오류 코드 `51402`를 반환한다.

## 관리자 목록과 상세

- 목록: `GET /api/v1/reports/admin?type={TargetType}&status={ReportStatus}`
- 상세: `GET /api/v1/reports/admin/{caseId}`

목록의 핵심 필드는 `caseId`, `targetAuthor`, `targetId`, `targetType`, `reportCount`, `status`, `decidedCategory`, `appliedPenalty`다. `type`과 `status`는 각각 단독 사용하거나 함께 사용할 수 있다.

상세에는 목록 필드에 더해 다음이 포함된다.

- `submissions`: 신고자별 제출 사유와 `submittedCategory`, `hasEvidence`
- `existingPenalties`: 대상 작성자의 기존 제재와 현재 활성 여부
- `moderationReason`, `processedByAdminId`, `processedAt`: 처리 감사 정보

## 관리자 승인·반려

`PATCH /api/v1/reports/admin/{caseId}/status`

승인:

```json
{
  "status": "RESOLVED",
  "decidedCategory": "INSULT_DEFAMATION",
  "reason": "관리자 확인 완료"
}
```

반려:

```json
{
  "status": "REJECTED",
  "decidedCategory": null,
  "reason": "신고 근거 부족"
}
```

`RESOLVED`에는 `decidedCategory`가 필수다. `RECEIVED`로의 변경과 재처리는 허용하지 않는다.

열거형 값:

- `TargetType`: `COMMUNITY`, `COMMUNITY_COMMENT`, `ACTIVITY`, `ACTIVITY_RECRUITMENT`, `USER`, `CHAT`
- `ReportStatus`: `RECEIVED`, `RESOLVED`, `REJECTED`
- `ReportCategory`: `BUSINESS_PROMOTION`, `INSULT_DEFAMATION`, `FALSE_INFORMATION`, `NO_SHOW_ABANDONMENT`, `HARASSMENT_THREAT`, `INAPPROPRIATE_PROFILE`, `SEXUAL_HARASSMENT`, `FRAUD`, `OTHER`
- `PenaltyType`: `WARNING`, `SUSPENDED_7_DAYS`, `PERMANENT_BAN`

## 이번 PR 이후 작업

### 7. 관리자 전용 콘텐츠 조치

현재 승인 처리에서는 일반 사용자용 삭제 서비스를 재사용한다. 채택된 답변, 권한 판정, 도메인 상태 규칙 때문에 신고 승인이 실패할 수 있으므로 후속 작업에서 관리자 전용 moderation 조치로 분리한다.

- 사용자 삭제 API의 작성자 권한 검사를 우회하는 명시적 관리자 경로
- 채택 답변·연관 통계·북마크 등 도메인 무결성을 보존하는 숨김 또는 소프트 삭제 정책
- 신고 승인과 콘텐츠 조치의 실패 상태를 분리해 재시도할 수 있는 운영 모델
- 프론트에는 `contentActionStatus`와 실패 사유를 제공할지 계약 확정

### 8. 운영 안전장치와 이의제기

다음 항목은 별도 PR에서 정책을 정한 뒤 구현한다.

- `title`, `context` 길이 제한과 ID 양수 검증
- 사용자·IP·시간 구간별 신고 rate limit 및 악의적 신고자 정책
- 제재 이의제기, 관리자 철회, 정정 이력을 append-only 감사 로그로 보관
- 신고 결과 알림의 실제 발송 시점과 채널 정의
- 증거 파일 보존 기간, 신고 반려·탈퇴 시 삭제 정책, 관리자 접근 로그
- 동시 접수·처리 부하 테스트와 운영 지표/알림

대상별 case 집계, 단독 `type` 필터, 처리 관리자 감사 필드, 채팅 참여자 검증은 이번 PR에 이미 반영되어 후속 범위에서 제외한다.
