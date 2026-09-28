# Running FluxGate tests

Requirements: JDK 21 and a running Docker daemon (for example, Docker Desktop).
Run commands from the repository root. Maven is supplied by `mvnw`.
The first run needs network access to download Maven dependencies and container images.

## Full verification

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

This compiles the application, runs the tests, and builds the application JAR.
Database tests start disposable PostgreSQL containers, apply Liquibase migrations,
and override the datasource configuration with temporary connection details.
They do not use the development database from `application.yml`.
You do not need to start FluxGate or run Docker Compose first.
Docker must be available; database tests fail rather than silently skip if it is not.

## Focused checks

Rate limiter only (no Docker required):

```bash
./mvnw -Dtest=InMemoryApiKeyRateLimitServiceTest test
```

HTTP behavior, security, and logging with mocked service/database dependencies
(no Docker required):

```bash
./mvnw -Dtest=GlobalExceptionHandlerTest,ApiSecurityWebMvcTest,RequestLoggingWebMvcTest test
```

Authentication and migrations against PostgreSQL (Docker required):

```bash
./mvnw -Dtest=ApiKeyAuthServiceTest test
```

The MVC tests exercise request handling and the security chain. The PostgreSQL
tests verify actual queries, committed writes, and migrations. The full application
test also checks that a chat response and its persisted request log share an ID.

## Continuous integration

`.github/workflows/ci.yml` runs full verification on pushes and pull requests with
Java 21 on Ubuntu. Testcontainers manages PostgreSQL; no database credentials or
provider API keys need to be configured as GitHub secrets.

After committing and pushing the workflow and application changes, check the
repository's **Actions** tab for the first run. Local verification does not confirm
that the hosted workflow has passed. Test reports are uploaded as `test-reports`
and retained for seven days, including when tests fail.

Locally, detailed results are available in `target/surefire-reports/`.

OpenAI adapter tests use a local HTTP server with fake credentials and never call
the paid API. See [OPENAI.md](OPENAI.md) for provider setup and focused test commands.
