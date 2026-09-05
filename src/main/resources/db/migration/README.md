# Flyway baseline policy

This service onboards an existing, Hibernate-managed schema to Flyway while also supporting an empty MySQL database.

- `V0__Initial_schema.sql` is the immutable schema snapshot from the entity model immediately before the report update.
- On an empty MySQL database, Flyway executes `V0` and then the report-domain delta in `V1`.
- On the first deployment to an existing non-empty database, `baseline-on-migrate` records version `0` without executing V0 and then executes `V1`.
- The existing database must match the pre-update entity model before V1 is deployed.
- All schema changes after this rollout must be added as immutable, sequential migrations.
- `V2__Report_multiple_evidence.sql` adds the report-to-evidence 1:N table, backfills each legacy evidence key, and removes the obsolete single-evidence column.
- `V6__Withdrawn_user_relationship_cleanup.sql` removes stale tag/follow relationships for already-withdrawn users and indexes inbound follow lookups.
- `V12__Add_campus_to_education.sql` adds an optional legacy-compatible campus reference to education and enforces that the selected campus belongs to the stored institution.
- Local and test H2 profiles disable Flyway because their disposable schema is created from JPA entities; the migration SQL targets MySQL.

Do not edit an already deployed migration. Add a new sequential migration instead.

## V13: remove phone numbers and deliver gifticons by email

- `V13__Remove_phone_numbers_and_use_gifticon_email.sql` preserves users, credentials, statuses, points, purchase IDs, and export/processing state. It removes `users.phone_num`, `gifticon_purchases.buyer_phone`, and `gifticon_purchases.recipient_phone`, including stored phone values.
- `buyer_email` expands from 200 to 255 characters, matching signup email validation. Existing snapshots take precedence; missing snapshots use the user's email when available.
- `recipient_email` is backfilled only for orders without an explicit recipient phone or with a recipient phone matching the buyer snapshot (falling back to the user's phone only if the snapshot is absent). Spaces and hyphens are ignored when comparing. Other recipients remain NULL; no email address is guessed.
- The exporter includes these unresolved orders with `deliveryStatus=EMAIL_REQUIRED`. Admins must confirm the intended recipient's email before external purchase/delivery. `buyerEmail` is a contact address, not a substitute recipient address.
- Already exported orders retain their batch and delivery state. Existing READY batches continue their normal retries; cached spreadsheets using the old columns are rebuilt in email format before sending. SUBMITTED/LEGACY_UNKNOWN batches are not queued again. Previously sent spreadsheets must be reconciled separately before continuing fulfillment.
- See [the rollout and API checklist](../../../../../docs/phone-number-removal.md) before deploying. Stop old application instances and export jobs before migrating because old versions still depend on the removed columns.

The single-column phone unique index is removed by MySQL when the column is dropped ([MySQL ALTER TABLE documentation](https://dev.mysql.com/doc/refman/8.4/en/alter-table.html)). Historical `V0` retains its original schema and checksum; empty databases execute through V13 to reach the new schema.
