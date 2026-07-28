# Report CodeRabbit review decisions

This note records the review items that are intentionally deferred or rejected, so they are not mistaken for missed work.

## Moderation transaction and retry contract

- `processReport` applies the penalty, removes the reported content, and changes the case/submission status in one database transaction.
- A non-ignorable content deletion failure propagates and rolls back the database changes. The case remains `RECEIVED`, so the same moderation request can be retried.
- A not-found result for content that was already removed is treated as an idempotent success. Other authorization, integrity, and infrastructure failures are not ignored.
- The case is changed to `RESOLVED` only after the content action succeeds or is confirmed to be already complete.

## Evidence finalization

Evidence finalization currently reuses the existing upload-ticket/S3 consume flow. The S3 move occurs inside the report creation transaction. Moving it after commit without a durable state machine would trade a long transaction for reports whose evidence can remain permanently unfinalized.

A later reliability change should introduce an explicit evidence state plus an outbox or retryable finalizer. It should not be implemented as a bare `afterCommit` callback. This PR intentionally does not redesign the shared presigned-upload infrastructure.

## Account restriction checks

- WebSocket `SEND` and `SUBSCRIBE` frames recheck account activity intentionally, so a restriction takes effect without waiting for reconnect.
- A cache is not introduced in this PR because it would require a defined revocation delay and invalidation policy.
- `AccountAccessGuard` does not acquire a pessimistic user-row lock. Locking only for the short guard transaction cannot make the subsequent business action atomic with the check, but would add contention to every guarded request. Penalty application remains serialized on the user row.

## Flyway scope

- V0 remains the immutable pre-report schema snapshot.
- V1 contains the report rollout delta, including the new upload-ticket enum values required by report evidence.
- Local/test H2 continues to use disposable Hibernate-created schemas. The MySQL-specific migration is validated in a MySQL deployment environment.
- MySQL DDL is implicitly committing. Splitting the existing rollout into more version files would not make each DDL step transactional; deployment should validate the migration against a copy of the target schema and retain a backup before first rollout.
