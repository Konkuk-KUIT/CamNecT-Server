# Flyway baseline policy

This service onboards an existing, Hibernate-managed schema to Flyway.

- Version `0` is a Flyway baseline marker configured in `application.yml`; it is not a SQL migration.
- On the first deployment to a non-empty database, Flyway records baseline version `0` and then executes `V1`.
- All schema changes after this rollout must be added as immutable, sequential migrations.
- Tests disable Flyway because their H2 schema is created from JPA entities.

This migration set assumes the pre-Flyway production schema already exists.
