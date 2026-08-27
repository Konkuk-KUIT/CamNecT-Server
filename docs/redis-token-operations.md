# Redis token session operations

## 1. What changed

CamNecT now treats a signed JWT as only one half of authentication. An access token must both:

1. have a valid JWT signature, type, and expiration; and
2. have its SHA-256 hash registered in the user's Redis session.

This makes logout, password changes, withdrawal, and suspension effective immediately without storing raw tokens. The database remains the source of truth for account and report-penalty status.

The active policy is:

- Every login creates a new UUID session ID (`sid`). The access and refresh tokens for that login carry the same `sid`; the frontend does not create, store, or send it separately.
- Multiple login sessions may coexist for a user, so logging in on a second device does not terminate the first device.
- Each session has one current refresh token. Refresh rotation preserves the `sid` and affects only that session.
- Refresh-token reuse revokes only the compromised session. Other devices remain logged in.
- Logout revokes the current access token's session. When `deviceId` is supplied, only that push device is disabled and other login sessions/devices remain active. During the frontend transition, a body-less legacy logout still disables all push devices.
- Password change/reset, withdrawal, a seven-day suspension, and a permanent ban revoke every login session for the user.
- `POST /api/auth/refresh` is active and rotates both tokens atomically in Redis. A successful client must replace both its access token and refresh token with the response values.
- Verification and password-reset JWTs are not Redis sessions. Their existing short-lived, purpose-specific validation remains unchanged.

## 2. Redis data model

For user `42`, with the default prefix, all sessions live in one versioned HASH:

```text
key: camnect:auth:session:v2:{42}
type: HASH

s:<sid>:a:<access-token-sha256> -> access JWT expiry epoch ms
s:<sid>:r                       -> current refresh-token-sha256
s:<sid>:re                      -> refresh JWT expiry epoch ms
```

The user HASH expires at the latest refresh expiry it contains. Login, refresh rotation, replay response, current-session logout, and all-session revocation are atomic Lua or single-key operations. Redis never receives a raw access or refresh token.

The hard-coded `v2:` namespace intentionally isolates this model from the earlier user-wide Redis keys. JWT access/refresh tokens issued before this deployment have no `sid` claim and are rejected, so no database or Redis migration is required; developer accounts must log in again.

## 3. Local development

`docker-compose.yml` includes Redis 7.4 with AOF persistence.

```bash
docker compose up -d db redis
```

The application defaults to `localhost:6379`. When the app runs inside Compose it receives `REDIS_HOST=redis`.

Tests set `app.auth.token-store=in-memory`, so the test suite does not require Docker or an external Redis instance.

## 4. AWS Console setup

The least surprising production fit for the current Spring configuration is **Amazon ElastiCache for Valkey, node-based, cluster mode disabled**. Valkey uses the Redis protocol, and the application can use the replication group's primary endpoint directly. For production, use one primary plus at least one replica with Multi-AZ automatic failover. For stage, a smaller single-node cache is acceptable if downtime and session loss are acceptable.

AWS Console steps:

1. Open **ElastiCache** in `ap-northeast-2` and choose **Valkey caches** → **Create cache**.
2. Select **Design your own cache**, **Cluster cache**, and **Cluster mode disabled**.
3. Choose the same VPC as the CamNecT EC2 instance. Select private subnets in at least two Availability Zones for production.
4. Enable **encryption in transit (TLS)** and **encryption at rest**. Do not expose the cache publicly.
5. Production: enable Multi-AZ, automatic failover, and at least one replica. Stage may use one small node.
6. Create or select a Redis security group, for example `camnect-redis-sg`. Add one inbound rule only:
   - Type: Custom TCP
   - Port: `6379`
   - Source: the EC2 application's security group, not an IP range and never `0.0.0.0/0`
7. Prefer an ElastiCache user/user group for authentication. Create an app-only user with a strong password and attach its user group to the cache. Keep the default user disabled when your chosen engine/configuration supports it.
8. After the cache is `Available`, copy the **Primary endpoint**. Do not include `rediss://` or the port in `REDIS_HOST`.

AWS documents the console creation flow and billing behavior in [Creating a cluster for Valkey or Redis OSS](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/Clusters.Create.html), TLS/AUTH connection behavior in [Creating a node-based ElastiCache cluster](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/SubnetGroups.designing-cluster-pre.redis.html), and endpoint lookup/connection checks in [Read and write data to the cache](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/GettingStarted.serverless-redis.step2.html).

## 5. EC2 environment variables

Add these to both `/path/to/.env.stage` and `/path/to/.env.prod` as appropriate. The repository's Compose files already load those files.

```dotenv
REDIS_HOST=camnect-production.xxxxxx.apn2.cache.amazonaws.com
REDIS_PORT=6379
REDIS_SSL_ENABLED=true
REDIS_USERNAME=camnect-app
REDIS_PASSWORD=<store-this-in-your-secret-system>
REDIS_CONNECT_TIMEOUT=2s
REDIS_TIMEOUT=2s
REDIS_AUTH_KEY_PREFIX=camnect:prod:auth:session:
```

Use `camnect:stage:auth:session:` in stage. If using the legacy single AUTH token rather than RBAC, leave `REDIS_USERNAME` empty and set only `REDIS_PASSWORD`.

The access-token default remains two days until the frontend refresh flow is deployed. After that rollout, shorten it deliberately; for short-lived stage QA, set `JWT_ACCESS_TOKEN_EXPIRATION_MS=120000` (two minutes). The refresh-token default remains 14 days and can be overridden with `JWT_REFRESH_TOKEN_EXPIRATION_MS`.

Do not commit these values. Prefer AWS Secrets Manager or SSM Parameter Store feeding the deployment environment. Redis credentials do not replace the security-group boundary or TLS.

## 6. Pre-deploy connection check

Run from the EC2 host, because ElastiCache is VPC-only:

```bash
valkey-cli -h "$REDIS_HOST" -p 6379 --tls --user "$REDIS_USERNAME" -a "$REDIS_PASSWORD" PING
```

Expected output is `PONG`. Avoid putting the password directly in shell history; the command above assumes it is already loaded into the process environment.

Then deploy and verify:

1. Login succeeds and creates the versioned user HASH with one session prefix.
2. HASH fields contain hashes and expiry timestamps, not JWT strings.
3. A second login adds a different `sid`; both devices remain authenticated.
4. Logout removes only the current `sid` fields and its old access token returns `401 / 41103`.
5. Applying a report suspension deletes the entire user HASH; requests return the suspension response from the database guard.
6. `POST /api/auth/refresh` with `{"refreshToken":"<token>"}` returns an `ApiResponse` whose `data` contains new access and refresh tokens.
7. Reusing the previous refresh token returns `401 / 41107` and revokes that `sid`, while another device's `sid` stays active.

## 7. Rollout and rollback notes

- Deploy Redis before deploying this application version.
- All access and refresh tokens issued before this deployment lack `sid` and return `401 / 41103`. Users must log in again.
- Rows in the legacy `user_refresh_tokens` MySQL table are no longer read or written. Keep the table during the rollback window; remove it later in a separate Flyway migration.
- Redis is fail-closed for active authentication: if it is unavailable, signed access tokens are not accepted. Account suspension checks remain database-backed.
- Redis data loss logs users out but does not lose account data. This is safe for authentication, though it affects availability.
- Monitor ElastiCache connection count, engine CPU, memory, evictions, and command latency. Token keys should expire naturally; evictions should be treated as forced logout and a capacity warning.

## 8. Frontend refresh contract

- Send `POST /api/auth/refresh` without an access-token `Authorization` header. The JSON body is `{"refreshToken":"<current-refresh-token>"}`.
- Read the new tokens from the HTTP response body's `data` object (`response.data.data` in Axios). Rotation invalidates the submitted refresh token, so replace both client values together before replaying the original request.
- `sid` is an internal JWT/Redis claim. The frontend contract does not add a `sessionId` field or header.
- Share one in-flight refresh operation across every HTTP client in the same application context. Separate browser tabs or PWA windows also need cross-context coordination if they share tokens.
- Treat `401` and `403` refresh responses as an invalid session. A network error or `5xx` response does not prove that the token is invalid; keep the local tokens and allow a later retry.
- STOMP authentication uses the access token, never the refresh token. The server checks the access-token hash again on every `SEND` and `SUBSCRIBE`, so reconnect STOMP with the newly issued access token after a successful refresh.
- If STOMP is the first channel to detect access-token expiration, route that authentication failure through the same single-flight refresh operation and then reconnect. An HTTP `401` is not guaranteed to occur first while the user stays in chat.
