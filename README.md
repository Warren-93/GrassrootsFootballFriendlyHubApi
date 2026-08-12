# Grassroots Football Friendly Hub — API

Spring Boot (Java 21) API serving the Kotlin Multiplatform Android and iOS
clients, and a future web client.

## Architecture decision: Java Spring Boot instead of Ktor

The Technical Specification proposed Kotlin + Ktor on the grounds of keeping the
ecosystem predominantly Kotlin-based. That has been changed to Java + Spring
Boot for a practical reason that outweighs it: the team is already productive in
Spring, and the client stack is unfamiliar territory. Learning one new thing
(Kotlin Multiplatform on the clients) is a better bet than two.

**What this changes**

- Section 3 and section 28 of the Technical Specification need updating. The
  "predominantly Kotlin" rationale in the conclusion is no longer accurate.
- Domain types are defined twice — once here in Java, once in the shared Kotlin
  module for the clients. This is mitigated, not eliminated, by generating the
  Kotlin API client from the OpenAPI contract that springdoc produces from these
  controllers. Regenerate the client whenever a DTO changes.

**What this does not change**

- Every screen, route, flow and validation rule in the two UX documents. The
  clients consume JSON over HTTPS and are indifferent to what produces it.
- The architectural principle that matters most: matching and business rules
  stay on the server, so scoring can be retuned without a mobile release.

**Escape hatch.** Spring Boot compiles Kotlin and Java in the same module. New
backend classes can be written in Kotlin later without a migration event.

## Layout

```
src/main/java/com/gffh/api/
├── domain/       Records and enums. No framework dependencies.
├── matching/     The compatibility engine. Pure logic, no Spring, no Mongo.
├── request/      Friendly request state machine.
├── service/      Orchestration, authorisation, persistence coordination.
├── repository/   Persistence interfaces.
├── web/          Controllers, DTOs, error handling.
└── config/       Security, CORS, request tracing.
```

`domain`, `matching` and `request` are deliberately free of Spring. They hold
the rules the specifications actually define, and they are unit-testable without
a container, a database or a web layer.

## Build state

**Builds, boots, and has been exercised end-to-end against a real MongoDB.**
The scaffold above was written without a JDK, Maven, or network access and had
never been through `javac`; it has since been compiled, and the gaps it
documented as outstanding (Mongo repository implementations, explicit indexes,
and the auth/availability/friendly-request/fixture controllers) have been
built out. `mvn clean verify` and `mvn spring-boot:run` both succeed, and the
full manager journey — register, create a team, publish availability, search
for opponents, send a friendly request, accept it, and read back the resulting
fixture with contact details disclosed — has been run against a live database
via curl, not just compiled — first against a portable MongoDB run directly as
a process, then again against `docker-compose.yml` once Docker Desktop was
available, with identical results both times (same worked-example score, same
fixture confirmation flow).

What *has* been verified: the scoring arithmetic in `MatchingEngine` was
reimplemented independently and checked against the worked example in the
Product Proposal and against the ranking and exclusion cases in
`CoreRulesHarness`, all 44 of which pass. The same worked-example score (99,
HIGH band) reproduces through the live API when two matching teams are
created via `POST /api/v1/teams`.

To build:

```bash
docker compose up -d
mvn clean verify
mvn spring-boot:run
```

The app needs a reachable MongoDB (`MONGODB_URI`, default
`mongodb://localhost:27017/db-gffh`) — `docker-compose.yml` provides one with a
named volume so data survives a restart. It generates its own RSA signing key at
startup for issuing bearer tokens — there is no external identity provider yet
(see `JwtKeyConfig`), so restarting the API invalidates outstanding tokens.

`CoreRulesHarness` is a dependency-free `main` that asserts the core rules. It
should be ported to JUnit 5 — it is the seed of the deterministic matching
test scenarios required by section 22 of the Technical Specification.

## Two calibration decisions

Both were made while checking the scoring arithmetic, and both differ from what
the specifications implied.

**Format gaps beyond one step are an exclusion, not a low score.** A 7v7 squad
cannot field an 11v11 side. The original weighting would have shown such a team
with a reduced score, which is worse than not showing it.

**The distance and availability curves were flattened less than first drafted.**
An early version scored every surviving candidate between 90 and 100, because
the hard exclusions had already removed everything genuinely unsuitable. The
score chip's three bands never fired, making the number decorative. The current
curves produce a realistic spread — a near-perfect match scores 99, a marginal
one 64 — so the bands carry information.

## Outstanding

Done since the scaffold above was written: auth (self-issued JWT, no external
IdP), availability, friendly-request and fixture controllers; Mongo repository
implementations for every persistence interface; explicit index creation
(`IndexConfig`); and a team-creation/management controller the original
scaffold didn't call out but which turned out to be required connective tissue
- nothing else has a team to act on without it.

Still open:

- **Email verification.** `Team.verification` and `BusinessRuleException.teamNotVerified()`
  already gate friendly-request sending on it, but nothing moves a team out of
  `NOT_STARTED`. Verified in this session's smoke test with a direct database
  write, not through the API.
- **The browser auth flow.** Bearer tokens suit the mobile clients but should not be
  reused for a web client; a cookie-based flow or a backend-for-frontend is
  needed. CORS is configured in anticipation.
- **JWT signing key persistence.** `JwtKeyConfig` generates a fresh RSA key pair
  on every startup (see its javadoc for why that's currently fine); this needs
  to change before horizontal scaling or long-lived refresh tokens.
- **`TeamRepository.findCandidates` takes a single `AgeGroup`, not a tolerance
  range.** `MatchingService` always passes the searching team's exact age group,
  so raising `ageBandTolerance` above zero via `MatchingWeightsProvider` would
  not surface the wider candidates the engine is otherwise willing to score.
  Harmless at the MVP default (tolerance 0), but worth fixing before that knob
  is exposed to admins.
- **Fixture completion.** `RequestStateMachine` models `CONFIRMED → COMPLETED`
  as a `SYSTEM` transition, implying a scheduled job once the fixture date has
  passed. No scheduler exists yet; fixtures stay `CONFIRMED` indefinitely.
- The six open questions in section 16 of the UI/UX Technical Specification.
  Age-band tolerance is already parameterised in `MatchingWeights`, so resolving
  that one is a configuration change rather than a code change (modulo the
  `findCandidates` gap above).
