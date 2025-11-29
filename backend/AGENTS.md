# **Repository Guidelines**

## Project Structure & Module Organization

* The project follows a layered architecture:
  **controller → service → repository**, with optional mapper and domain packages when needed.
* `src/main/java` contains production code structured by package. Use clear separation between:

    * controllers,
    * services,
    * repositories,
    * domain models,
    * DTOs,
    * configuration classes.
* `src/main/resources` contains `application.properties` (or `.yaml`) and Liquibase changelogs under `db/changelog`.
* `src/test/java` mirrors the package structure.
  **All integration tests extend `AbstractApplicationTest`** (details in Testing Guidelines).
* Root files include: `pom.xml`, Maven wrapper scripts, and optional infrastructure descriptors such as `docker-compose.yml`.

## Build, Test, and Development Commands

* `./mvnw clean install` — full build including unit and integration tests.
* `./mvnw test` — executes all test suites.
* `./mvnw spring-boot:run` — runs the service locally using the active profile.
* `docker-compose up -d` — optional: starts PostgreSQL/Kafka services needed for local development.

## Coding Style & Naming Conventions

* Use **Java 21** and **Spring Boot 3.5+**.
* Use **constructor injection** (preferably Lombok `@RequiredArgsConstructor`). Field injection is not permitted.
* Logging must use Lombok `@Slf4j`.
* Names should be explicit: `UserController`, `UserService`, `UserRepository`, `UserDto`.
* DTOs and immutable structures may use Lombok `@Value` or builders.
* Avoid “magic” strings or numbers; extract constants into `private static final` fields.
* When working with collections:
  * Prefer `getFirst()` over `get(0)` for clarity and intent.
* **Do not use `Stream.peek()`** in production code.
* **Do not use `@SneakyThrows`** in production (allowed only in test classes).
* Time handling:

    * All timestamps use **`Instant`**.
    * PostgreSQL columns must be **`timestamp without time zone`**.
    * Do not use `Date`, `Timestamp`, or `LocalDateTime` unless explicitly required.
    * Internal logic must work exclusively with `Instant`; time-zone conversion happens only at system boundaries.

## Testing Guidelines

### Integration Tests

* **All controller tests and all integration tests must use `@SpringBootTest`.**
  Spring test slices such as `@WebMvcTest` or `@DataJpaTest` are **forbidden**.
* A shared base test class named **`AbstractApplicationTest`** must be used for **all integration tests**.

  Updates to mocking rules:

  * The Spring `@MockBean` annotation is **deprecated and must not be used**.
  * All mocked beans must be defined using **`@MockitoBean`**.
  * `@MockitoBean` **may only be used inside `AbstractApplicationTest`**.
  * If a test requires additional mocks, they must be added to the abstract class — **never** in the child test.
  * This ensures that **a single application context is shared across the entire test suite**.
* Child integration test classes may use `@Autowired`; this does **not** trigger context reload.
* **`@DirtiesContext` is prohibited**.
* Avoid nested test classes whenever possible; prefer flat, explicit test structures.

### Unit Tests

* Unit tests must **not** extend `AbstractApplicationTest`.
* Unit tests run without a Spring context and rely on pure Mockito or plain Java testing.

## Database & Liquibase Guidelines

* All schema changes must be performed through Liquibase changesets.
* Each changeset must describe **one isolated, idempotent change**.
* Text fields must use PostgreSQL `text`; length restrictions should be expressed via CHECK constraints or application-level validation.
* Do **not** use `SERIAL` or `BIGSERIAL`.
  Preferred identity strategy:

    * `GENERATED … AS IDENTITY` or
    * sequence-based generation with JPA (`@SequenceGenerator`), using `allocationSize > 1` when needed.
* Default primary key type is **numeric** (`INTEGER` or `BIGINT`).
* UUID primary keys are allowed only when:

    * generated externally (e.g., message adapters),
    * sequential scanning must be prevented,
    * required by external systems or frameworks,
    * compatibility reasons apply,
    * or another explicit justification is documented.

## Commit & Pull Request Guidelines

* Use concise, action-oriented commit messages (e.g., `Add message processing endpoint`).
* Keep PRs focused and small; avoid combining unrelated refactors with feature work.
* Each PR should include:

    * a summary of intent,
    * list of changes,
    * test evidence,
    * references to tickets/issues when applicable.

### Patch Generation Rules (for all code-editing operations)

When generating file modifications, Codex must **always** output a
**raw unified diff (git-style patch)** and nothing else.

Strict requirements:

1. Patches must start with:
diff --git a/<path> b/<path>

markdown
Копировать код
2. Do **NOT** use Codex internal formats such as:
- `*** Begin Patch`
- `*** Update File`
- `*** End Patch`
3. Do **NOT** wrap patches in:
- Markdown code fences (```…```)
- PowerShell here-strings (`@' … '@`)
- JSON objects
- HTML or escaped formats
4. The output must be a **clean UTF-8 unified diff**, suitable for
`git apply` or Codex `--codex-run-as-apply-patch`.
5. If a diff cannot be produced, Codex must output the **full updated file**
instead of generating a pseudo-patch.

This rule is mandatory. Codex must not generate non-diff patch formats.


## Security & Configuration Notes

* Never commit secrets or credentials. Use environment variables or `.env` files excluded from version control.
* Document newly added Kafka topics or Liquibase changes so contributors know how to provision or update local infrastructure.