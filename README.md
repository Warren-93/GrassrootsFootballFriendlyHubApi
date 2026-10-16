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
├── push/         FCM (Android) and APNs (iOS) senders.
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
`MatchingEngineTest`/`RequestStateMachineTest` (`mvn test`), all 16 of which
pass. The same worked-example score (99, HIGH band) reproduces through the
live API when two matching teams are created via `POST /api/v1/teams`.

To build:

```bash
docker compose up -d
mvn clean verify
mvn spring-boot:run
```

The app needs a reachable MongoDB (`MONGODB_URI`, default
`mongodb://localhost:27017/db-gffh`) — `docker-compose.yml` provides one with a
named volume so data survives a restart. It signs bearer tokens itself with the
RSA key in `JWT_PRIVATE_KEY`, or with a throwaway key generated per startup
when that is unset (see `JwtKeyConfig`), in which case a restart invalidates
outstanding tokens. Everything else it reads is listed under Configuration
below.

`MatchingEngineTest` and `RequestStateMachineTest` are the deterministic
matching test scenarios required by section 22 of the Technical
Specification, run by `mvn test` like any other JUnit 5 test.

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

## Configuration

All environment variables are optional except `MONGODB_URI` in a deployed
environment. `render.yaml` declares the ones a deployment is expected to set
(Render supplies `PORT` itself); `application.yml` shows their defaults, and the
`SPRING_MAIL_*` ones bind straight to Spring Boot's `spring.mail.*`.

| Variable | What it does |
| --- | --- |
| `MONGODB_URI` | MongoDB connection string. Default `mongodb://localhost:27017/db-gffh`. |
| `PORT` | HTTP port. Default 8090. |
| `JWT_PRIVATE_KEY` | PKCS#8 PEM RSA key that signs access tokens. Unset: a key is generated per startup. |
| `CORS_ALLOWED_ORIGINS` | Comma-separated exact origins allowed to call the API (`*.vercel.app` previews are always allowed). |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP server. With no host, no email is sent. |
| `MAIL_FROM` | Sender, e.g. `Kickabout <noreply@example.com>`. Email is "configured" once this and the host are set. |
| `APP_BASE_URL` | The web app that links in emails point at (including member invitations' `/register?email=` link). |
| `SUPPORT_EMAIL` | Help address the clients show, served by `GET /api/v1/config`. Default: the bare address inside `MAIL_FROM`, else none. |
| `GOOGLE_CLIENT_ID` | Google OAuth *Web* client ID. Turns Google sign-in on (`POST /api/v1/auth/oauth/google`); also Android's `serverClientId` and an accepted token audience. |
| `GOOGLE_ADDITIONAL_CLIENT_IDS` | More accepted Google audiences, comma-separated (e.g. an iOS client ID). A Google token for any other client is refused. |
| `APPLE_SERVICES_ID` | Apple Services ID for web sign-in (an accepted audience). |
| `APPLE_BUNDLE_ID` | The iOS app's bundle ID (the audience of native iOS tokens). Either Apple variable turns Apple sign-in on (`POST /api/v1/auth/oauth/apple`). |
| `FCM_SERVICE_ACCOUNT_JSON` | Firebase service-account JSON (raw or base64) for Android push through FCM HTTP v1. The project ID is read from it. Unset: no Android pushes. |
| `APNS_KEY_ID`, `APNS_TEAM_ID`, `APNS_PRIVATE_KEY`, `APNS_BUNDLE_ID` | iOS push through APNs with token auth: the .p8 key's ID, the Apple team ID, the .p8 file's PEM contents, and the app's bundle ID (the topic). Off until all four are set. |
| `APNS_PRODUCTION` | `true` sends to api.push.apple.com (App Store and TestFlight builds); default `false` uses the sandbox, which only development builds receive from. |
| `ADMIN_TOTP_REQUIRED` | `true` makes admin sign-in also ask for an authenticator code. Default `false`. |
| `AUTH_COOKIE_SAME_SITE`, `AUTH_COOKIE_SECURE` | The web app's refresh-token cookie. Defaults `None` and `true`. |

`GET /api/v1/config` is public and tells the clients the support address and
which sign-in providers are on, so none of these IDs need building into them.
`apple.iosEnabled` is true only when `APPLE_BUNDLE_ID` is set, so the iOS app
doesn't offer Apple sign-in when only the web Services ID is configured.

Google and Apple sign-in check each ID token against the provider's published
signing keys (fetched from Google and Apple on first use, then cached), its
issuer, its audience (one of the IDs above) and its expiry; Google must also
have verified the email. The account with that email is signed in, or created
already verified and with no password (password sign-in then needs "Forgot
password" first). An existing account that wasn't verified yet is verified,
and loses its password and other sessions, since nobody had proved they owned
the address when that password was set. The answer is the same as
`POST /api/v1/auth/login`'s, cookie flow included, plus `newUser`. A provider
that isn't configured answers 400 `OAUTH_PROVIDER_DISABLED`, a bad token 401
`INVALID_OAUTH_TOKEN`, and 503 `OAUTH_PROVIDER_UNAVAILABLE` when the
provider's keys can't be fetched.

Until email is configured, verification and password-reset tokens are logged
and returned to the client instead of emailed, which means account email
verification proves nothing in that mode, and no fixture confirmation,
notification or member invitation emails are sent. Invitations are still
created, but since a verification link that was never emailed proves nothing,
they then only become memberships through an admin's "verify email now" or
Google/Apple sign-in. A verification link only ever verifies the address it
was sent to, so changing email to another address makes earlier links useless.

Push is optional per platform: the apps can register device tokens
(`POST /api/v1/me/push-tokens`) whether or not the server has credentials,
and start receiving pushes as soon as it does. Tokens FCM or APNs report as
invalid are deleted, as are an account's tokens when it is deleted.

## Outstanding

Done since the scaffold above was written: auth (self-issued JWT, no external
IdP), availability, friendly-request and fixture controllers; Mongo repository
implementations for every persistence interface; explicit index creation
(`IndexConfig`); and a team-creation/management controller the original
scaffold didn't call out but which turned out to be required connective tissue
- nothing else has a team to act on without it.

Also done since: email delivery over SMTP (`EmailService`: verification and
password-reset links, and fixture confirmations to both teams via
`FixtureConfirmationEmails`); team verification through the API (submission,
ADM-03's two-admin review queue, and an admin "verify now" override in
`VerificationService`); automatic fixture completion (`FixtureCompletionJob`
checks hourly and moves CONFIRMED fixtures whose date has passed to
COMPLETED); venues filled in from the home team's default when a request or
fixture has none; and `TeamRepository.findCandidates` taking the set of age
groups within `ageBandTolerance` (still 0 by default, so exact age group only).

Also done since: member invitations (`MemberInviteService`: adding an address
with no account invites it by email, and the invite becomes a membership once
that address is verified - by the emailed link, an admin's "verify email now",
or `acceptPendingInvites` from any other sign-in that proves ownership), and every
in-app notification also delivered by email and push (`NotificationDelivery`,
`PushService`, `FcmSender`, `ApnsSender`), each behind the manager's
`emailEnabled`/`pushEnabled` preferences.

Also done since: Google and Apple sign-in (`POST /api/v1/auth/oauth/google`
and `/apple`, checked by `OAuthTokenVerifier` against `OAuthTokenConfig`'s
rules), sharing login's rate limit, suspension check and refresh cookie, and
accepting waiting team invites since the provider has proved the address.

Also done since: read-only reach below management (`MembershipService`'s
`canReadTeam` and `canManageAnyTeamInClub`). Anyone on a team, including a
member who joined with a code, can list and open its fixtures, without the
managers' contact details and without being able to cancel; any team manager
in a club can list its venues, which only club admins can change; and every
notification preference flag is optional on update, so a partial body
switches nothing else off. `GET /api/v1/teams/mine` says which applies to
each team: `canManage` (manage the team) and `canManageClub` (a club admin,
so can add, edit, make default and delete the club's venues), both left out of
every other team view, so clients can hide what a team manager or member would
be refused.

Also done since: only a club admin can grant the club admin role (adding a
member or inviting an address as `CLUB_ADMIN`, or changing someone's role to
it) or change or remove a membership that has it
(`MembershipService.requireCanManageClubAdmins`). A team manager still adds,
invites, changes and removes players and other team managers, but gets 403
`CLUB_ADMIN_ONLY` for anything touching a club admin, so they can't promote
themselves into the club-wide powers (venues, the club, every squad) the role
carries. `LAST_CLUB_ADMIN` still stops a club losing its only admin, invites
already sent as `CLUB_ADMIN` still join as one, and any manager of the team
can still cancel a pending invite. Clients offer the club admin role, and the
controls on a club admin's row, only where `canManageClub` isn't false.

Still open:

- **Browser auth across sites.** The web client's refresh token is an httpOnly
  `gffh_refresh` cookie (`X-Auth-Mode: cookie`, see `RefreshCookies`). The web
  app's production build calls the API through its own `/api` proxy on Vercel,
  so the cookie is first-party and Safari keeps it. The proxy marks calls from
  its own site with `X-Web-Proxy: same-origin` (and drops their Origin), so
  the cookie works without the site being in `CORS_ALLOWED_ORIGINS`. Refresh tokens rotate on
  every use, but a token replaced in the last 60 seconds still works (unless
  signed out or revoked), so a reload mid-refresh or two tabs opening at once
  don't sign the user out.
- **JWT signing key in deployed environments.** `JwtKeyConfig` signs with the
  PKCS#8 PEM key in `JWT_PRIVATE_KEY`, and only generates a throwaway key per
  startup when that is unset. Set it on Render (`openssl genpkey -algorithm RSA
  -pkeyopt rsa_keygen_bits:2048`) so access tokens survive restarts.
- **Push against the live services.** `FcmSender` and `ApnsSender` are
  tested against local stand-ins for Google's and Apple's endpoints, not yet
  against real FCM and APNs. Once `FCM_SERVICE_ACCOUNT_JSON` and the `APNS_*`
  values are set, send a friendly request to a team whose manager is signed in
  on a phone, and check the logs for `push rejected` lines.
- **Google and Apple sign-in against the live providers.** Token checks are
  tested with a locally generated key standing in for Google's and Apple's
  published ones. Once `GOOGLE_CLIENT_ID` (plus any iOS client in
  `GOOGLE_ADDITIONAL_CLIENT_IDS`) and the `APPLE_*` IDs are set, sign in from
  each client and check the logs: a `sign-in token rejected` line ending
  `The aud claim is not valid` means that client's ID is missing from those
  settings.
- **Venue postcodes.** Venues accept an optional `postcode` on create and
  update, and otherwise take one from the end of the address. Clients that
  collect a postcode only to look up coordinates should send it too, so
  fixture emails and venue details can show it.
- The six open questions in section 16 of the UI/UX Technical Specification.
  Age-band tolerance is already parameterised in `MatchingWeights` and honoured
  by the candidate query, so resolving that one is a configuration change
  rather than a code change.
