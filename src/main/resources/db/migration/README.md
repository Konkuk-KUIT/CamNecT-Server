# Flyway baseline policy

This service onboards an existing, Hibernate-managed schema to Flyway while also supporting an empty MySQL database.

- `V0__Initial_schema.sql` is the immutable schema snapshot from the entity model immediately before the report update.
- On an empty MySQL database, Flyway executes `V0` and then the report-domain delta in `V1`.
- On the first deployment to an existing non-empty database, `baseline-on-migrate` records version `0` without executing V0 and then executes `V1`.
- The existing database must match the pre-update entity model before V1 is deployed.
- All schema changes after this rollout must be added as immutable, sequential migrations.
- Local and test H2 profiles disable Flyway because their disposable schema is created from JPA entities; the migration SQL targets MySQL.

Do not edit V0 or V1 after deployment. Add a new migration instead.
