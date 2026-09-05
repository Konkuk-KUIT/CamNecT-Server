# 전화번호 수집 중단 및 기프티콘 이메일 전환

## API 변경

| 기능 | 변경 |
| --- | --- |
| `POST /api/auth/signup/email/verify` | `phoneNum` 입력 및 중복 검사 제거. 이메일 인증·아이디/이메일 중복 검사는 유지. |
| `GET /api/profile/me/settings` | 응답의 `phoneNum` 제거. 기존 `email` 사용. |
| `GET /api/admin/verification/documents`, `GET /api/admin/verification/documents/{submissionId}` | `phoneNum`을 `email`로 교체. |
| `GET /api/gifticons/home` | 응답의 `phoneNum`을 `email`로 교체. |
| `POST /api/gifticons/purchases/confirm` | `recipientPhone` 대신 선택적 `recipientEmail` 사용. 미입력/공백이면 구매자 가입 이메일을 사용. 앞뒤 공백 제거, 이메일 형식 및 최대 255자 검증. 수신 주소가 유효하지 않으면 `400 / 47003`, 요청 필드 형식 오류는 `400 / 40000`. |

구매 요청은 알 수 없는 필드를 거부한다. 구버전 클라이언트가 `recipientPhone`을 보내면 주문을 거절하여 수신 정보가 무시된 채 구매자에게 발송되는 일을 막는다. 회원가입 화면과 관리자 화면을 포함한 클라이언트도 새 계약에 맞춰 전화번호 입력·표시·전송을 제거해야 한다.

```json
{
  "productId": 10,
  "quantity": 1,
  "spendPoints": 1000,
  "clientRequestId": "unique-order-id",
  "recipientEmail": "recipient@example.com"
}
```

자기 구매는 `recipientEmail`을 생략할 수 있다. 실제 수신 이메일은 구매 시점에 저장한다. 같은 `clientRequestId`의 재시도는 기존 구매의 이메일 스냅샷과 비교하며 수신 이메일이 달라지면 `409 / 47901`로 거절한다.

## 관리자 엑셀 처리

서버는 기프티콘을 직접 구매하거나 사용자에게 발송하지 않는다. 기존처럼 구매요청을 엑셀로 내보내 관리자에게 메일로 전달한다. 상품 공급사 클라이언트는 상품 목록 동기화만 수행한다.

- `buyerPhone` 제거, `recipientPhone`을 `recipientEmail`로 교체.
- `buyerEmail`: 구매자 이메일 스냅샷. 문의 시 사용.
- `recipientEmail`: 실제 외부 구매·발송에 사용할 이메일.
- `deliveryStatus=READY`: 유효한 수신 이메일이 있는 주문.
- `deliveryStatus=EMAIL_REQUIRED`: 기존 선물 주문 등 수신 이메일을 확정할 수 없는 주문. 발송을 보류하고 구매자에게 수신 이메일을 확인한다. 구매자 이메일로 임의 대체하지 않는다.

이메일 확인이 필요한 주문도 엑셀에 포함하므로 관리자에게 누락되지 않는다. 확인한 주소는 관리자 작업용 엑셀에 기입한 뒤 처리한다. 확인되지 않은 주문을 발송 완료로 취급하지 않는다. `export_batch_id`/`exported_at`은 엑셀 추출 여부이며 발송 완료 여부가 아니다.

관리자 메일 발송 재시도와 배치 상태 추적은 유지한다. 재시도 대기 중인 기존 배치의 서버 엑셀에 옛 컬럼이 있으면, 전환된 DB 데이터를 사용해 이메일 형식으로 파일을 재생성한 뒤 발송한다. 엑셀 행의 `deliveryStatus`는 수신 이메일 확인 여부이며, DB 배치의 `delivery_status`는 관리자 메일 전송 상태로 서로 다른 값이다.

## 기존 DB 전환과 배포

1. 클라이언트의 전화번호 입력·표시·전송을 제거하고 새 이메일 필드에 맞춘 버전을 준비한다. 구버전 구매 요청은 서버가 거절하므로 해당 버전의 구매 기능을 차단하거나 업데이트를 유도한다.
2. 구버전 서버와 기프티콘 스케줄러를 중지해 마이그레이션 중 주문·엑셀 생성이 발생하지 않게 한다. 운영 복제본에서 V13을 먼저 검증하고 기존 주문의 처리 상태와 필요한 수신 이메일을 확인한다.
3. 새 서버 배포 시 Flyway가 V13을 실행한다. 이미 배포된 V0~V12는 수정하지 않는다. 사용자 계정·비밀번호·상태·포인트·구매 내역은 유지하고 전화번호 3개 컬럼과 저장값을 삭제한다. 기존 전화번호가 있는 사용자에게 재가입을 요구하지 않는다.
4. V13은 기존 구매자 이메일 스냅샷을 우선 사용한다. 스냅샷이 없으면 계정 이메일을 사용한다. 본인 수신을 확인할 수 있는 주문만 수신 이메일을 채우며, 다른 사람의 전화번호로 지정한 선물 주문과 이메일이 없는 주문은 수신 이메일을 비워 둔다.
5. 아래 조회로 이메일 확인 대상과 기존 추출 여부를 확인한다. 기존 배치와 전송 상태는 유지한다. `READY` 배치는 정해진 메일 재시도를 계속하고, `SUBMITTED`/`LEGACY_UNKNOWN` 배치는 다시 대기열에 넣지 않는다. 이미 관리자에게 보낸 과거 엑셀의 미처리 주문은 최신 이메일 정보를 기준으로 관리자가 별도로 정리한다.
6. 가입·로그인·환경설정·서류심사·구매·관리자 엑셀을 확인하고 스케줄러를 재개한다. 컬럼 삭제 후 구버전 서버를 다시 실행하면 호환되지 않는다. 장애 시 새 스키마에 맞는 코드로 수정 배포한다. 전화번호는 마이그레이션만 되돌려 복구할 수 없다.

```sql
SELECT purchase_id, user_id, buyer_email, recipient_email,
       export_batch_id, exported_at, admin_processed_at, admin_success
FROM gifticon_purchases
WHERE recipient_email IS NULL OR TRIM(recipient_email) = ''
ORDER BY requested_at;
```

DB 마이그레이션은 이미 생성된 서버 엑셀 파일, 관리자 메일의 첨부파일·다운로드본, DB 백업·로그의 전화번호까지 삭제하지 않는다. 기존 주문의 이메일 전환 및 중복 발송 여부를 대조한 후 해당 보관본을 운영 정책에 따라 정리해야 한다. 이 저장소 변경은 운영 DB·메일함·외부 발송 서비스에 직접 적용하지 않는다.

## 검증 범위

`gradlew.bat --offline test`로 전체 테스트를 실행한다. 회귀 테스트는 전화번호 없는 회원가입, 기본·선물 수신 이메일 저장, 주소 오류 시 포인트 미차감, 중복 요청 처리, 구버전 수신 필드 거절, 실제 XLSX 셀과 보류 상태를 검증한다.

`PhoneNumberRemovalMigrationTest`는 빈 스키마와 기존 사용자·주문 데이터에 V13 SQL 원문을 실행해 전화번호 컬럼 삭제, 이메일 전환, 계정·포인트·추출 상태 보존을 확인한다. 이 테스트는 H2 MySQL 모드 기준이며 실제 MySQL의 DDL·잠금 동작 및 V0~V13 전체 Flyway 실행 검증을 대신하지 않는다.
