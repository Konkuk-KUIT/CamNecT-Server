# 프론트엔드 Refresh Token 연동 계약

## 1. 현재 배포 전제

- Access Token 기본 수명은 아직 **2일**이다. 프론트 refresh 배포가 확인된 뒤 별도 작업으로 단축한다.
- Refresh Token 기본 수명은 14일이다.
- 기존에 발급된 access/refresh token에는 `sid`가 없으므로 이번 백엔드 배포 후 모두 `401 / 41103`이 된다. 개발 계정은 다시 로그인해야 한다.
- `sid`는 백엔드가 JWT와 Redis에서만 관리한다. 프론트가 `sessionId`를 생성하거나 별도 필드·헤더로 보낼 일은 없다.

## 2. 로그인과 다중 기기

기존 로그인 응답 계약은 바뀌지 않는다. 로그인할 때마다 백엔드가 새 `sid`를 만들고, 응답한 access/refresh token에 같은 `sid`를 넣는다.

- 다른 기기에서 로그인해도 기존 기기는 유지된다.
- 같은 기기의 refresh에서는 기존 `sid`가 유지된다.
- `deviceId`나 FCM 토큰과 인증 `sid`는 서로 다른 개념이다.

## 3. Refresh API

```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "<현재 refresh token>"
}
```

이 요청에는 Access Token `Authorization` 헤더를 붙이지 않는다.

성공 응답:

```json
{
  "status": 200,
  "message": "성공하였습니다.",
  "data": {
    "tokenType": "Bearer",
    "accessToken": "<new access token>",
    "accessTokenExpiresInMs": 172800000,
    "refreshToken": "<new refresh token>",
    "refreshTokenExpiresInMs": 1209600000
  }
}
```

Axios 기준 토큰 위치는 `response.data.data.accessToken`과 `response.data.data.refreshToken`이다. Refresh Token Rotation을 사용하므로 성공 시 두 토큰을 한 번에 교체한 뒤 원래 요청을 한 번만 재시도한다. 제출했던 refresh token은 즉시 재사용할 수 없다.

주요 실패:

- `400 / 40000`: refreshToken 누락·공백·길이 초과 또는 잘못된 JSON
- `401 / 41103`: 변조, 알 수 없음, 로그아웃/보안 조치로 폐기됨, 또는 구형 `sid` 없는 토큰
- `401 / 41106`: Refresh Token 대신 다른 타입의 JWT 제출
- `401 / 41107`: 이미 회전된 Refresh Token 재사용. 해당 로그인 세션은 서버에서 폐기됨
- `401 / 41108`: Refresh Token 자체 만료
- `403 / 41302`, `41303`: 정지 또는 탈퇴 계정
- `503 / 50310`: Redis 일시 장애

`401`, `403`이면 로컬 인증 정보를 비우고 로그인으로 보낸다. 네트워크 오류나 `5xx`만으로 토큰이 무효라고 단정하지 말고, 로컬 토큰을 유지한 채 나중에 재시도할 수 있게 한다.

## 4. 동시 요청

여러 API가 동시에 Access Token 만료를 감지해도 refresh 요청은 하나만 실행해야 한다. 모든 HTTP 클라이언트가 같은 in-flight Promise를 공유하고, 나머지 요청은 그 결과를 기다렸다가 새 Access Token으로 각자 한 번만 재시도한다.

브라우저 탭/PWA 창이 토큰 저장소를 공유한다면 탭 사이에도 같은 조정이 필요하다. 같은 refresh token을 두 요청이 동시에 사용하면 첫 요청은 성공할 수 있지만 두 번째 요청은 재사용 탐지로 해당 `sid`를 폐기한다.

Refresh 전용 Axios 인스턴스에는 일반 API의 401-refresh 인터셉터를 붙이지 않아야 무한 재귀를 피할 수 있다.

## 5. STOMP 연결

STOMP는 Refresh Token이 아니라 Access Token으로 `CONNECT`한다. 서버는 `CONNECT` 시 저장한 access token hash를 이후 모든 `SEND`와 `SUBSCRIBE`에서도 다시 확인한다.

따라서 refresh 성공 후 기존 소켓에 Refresh Token을 보내는 것이 아니다. 기존 연결을 끊고, **새 Access Token**을 `Authorization: Bearer <new-access-token>`으로 넣어 다시 연결·구독한다.

STOMP가 HTTP보다 먼저 Access Token 만료를 감지할 수 있으므로, STOMP 인증 오류도 HTTP와 동일한 single-flight refresh 함수로 보내고 성공 후 재연결해야 한다.

## 6. 로그아웃

```http
POST /api/auth/logout
Authorization: Bearer <현재 access token>
Content-Type: application/json

{
  "deviceId": "<푸시 토큰 등록 시 사용한 현재 기기 ID>"
}
```

백엔드는 이 Access Token의 `sid`와 요청한 `deviceId`의 푸시 등록만 폐기한다. 다른 기기의 인증 세션과 푸시는 유지된다. 프론트는 현재 앱 컨텍스트에 저장한 access/refresh token을 함께 삭제하고 소켓을 종료한다.

프론트 전환 기간에는 본문 없는 기존 요청도 허용하지만, 이 경우 기존 동작대로 사용자의 모든 푸시 기기가 비활성화된다. 프론트에서 `deviceId` 전송이 배포된 뒤에는 요청 본문을 필수로 강화할 예정이다.
