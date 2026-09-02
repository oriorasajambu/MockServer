# MockServer

A portable HTTP mock server that runs on an Android phone.

An embedded Ktor CIO engine binds to a local port, so any device on the same Wi-Fi — or on the
phone's hotspot — can call `http://<phone-ip>:<port>/...`, and other apps on the same device can
call `http://localhost:<port>/...`. It keeps serving with the screen off.

It exists to build client UIs before a backend does, to trigger error states on demand, and to
demo an integration with no network at all.

```bash
./gradlew build          # compiles everything, runs unit tests, compiles androidTest, lints
./gradlew testDebugUnitTest
```

Built from the specification in `SPEC-1.md`. Where the code departs from it, the KDoc at that
point says so and why.

---

## 1. What it does

| | |
|---|---|
| **Embedded server** | Ktor CIO on `0.0.0.0:<port>`, with auto-fallback over the next ten ports |
| **Routes** | Collections with base-path prefixes; method, path pattern, header/query/body matchers |
| **Matching** | Most-specific-wins scoring with a five-step tie-break, so resolution is total and deterministic |
| **Responses** | Status, headers, static body with `{{path.x}}` / `{{query.x}}` / `{{header.x}}` placeholders |
| **Traffic inspector** | In-memory live log, auto-follow, filters, full-text search, cURL and HAR export |
| **Chaos** | Delay, injected error statuses, connection resets, truncated bodies — seeded, so failures replay |
| **Import** | OpenAPI 3.0/3.1 (JSON and YAML), Postman v2.1, HAR 1.2 |
| **Export** | Native JSON, Postman v2.1, HAR 1.2, cURL |
| **Discovery** | Ranked address list, QR code, mDNS/NSD advertisement |
| **Access control** | Optional API-key header |

Not in v1: traffic interception of other apps, a browser admin UI, HTTPS, JSONPath or regex
matchers, WebSocket/SSE, scenario state machines, tablet or landscape layouts.

---

## 2. Module graph

```
:app                     Application, MainActivity, NavHost, DI aggregation — nothing else
│
├── :core:common          Pure Kotlin. AppResult, DomainError, safeCall, AppConstants, Logger.
├── :core:domain          Pure Kotlin. Models, repository interfaces, use cases — the whole
│                         matching and chaos decision surface.
├── :core:server          Pure Kotlin. Ktor CIO engine and its adapters. The only module that
│                         imports Ktor.
├── :core:importexport    Pure Kotlin. OpenAPI / Postman / HAR / native wire formats.
├── :core:navigation      Pure Kotlin. @Serializable route contracts only.
├── :core:designsystem    AppTheme, colour/type/shape tokens, Dimens.
├── :core:ui              MviViewModel, ObserveAsEvents, DomainError → @StringRes, shared widgets.
├── :core:data            Room, DataStore, the foreground service, NSD, QR, repository impls.
├── :core:testing         MainDispatcherRule, fakes. Consumed via testImplementation.
│
└── :feature:{server,routes,traffic,settings}
```

### Dependency rules

```
:app ──► :feature:*  ──►  :core:*
:feature:a  ✗  :feature:b        never — route through :core:navigation
:core:*     ✗  :feature:*        never
:core:common depends on nothing
```

**Four modules apply `mockserver.jvm.library` rather than the Android plugin**: `:core:common`,
`:core:domain`, `:core:server` and `:core:importexport`. "No Android imports below the
presentation layer" becomes a compile error instead of a review comment — and, more usefully, it
means the matching pipeline, the chaos engine, the Ktor request path and every importer can be
exercised on a plain JVM with no device and no emulator.

**Everything in a feature module is `internal` except the navigation entry point.** A feature's
public API is `fun NavGraphBuilder.serverScreen(...)` and nothing else.

**Bindings live with their implementations.** Each has its own `internal @Module`; a central
`:core:di` would have to depend on every feature, inverting the direction the graph enforces.

Three features depend on `:core:data`, which is the one documented exception to a feature seeing
an implementation module: starting the foreground service, encoding a QR bitmap, opening battery
settings and issuing a loopback test request are platform mechanisms with no domain meaning, so
there is no repository interface for them to hide behind.

---

## 3. The parts worth understanding first

### The domain owns every decision

`HandleMockRequestUseCase` is the single entry point the Ktor engine calls. It runs the whole
SPEC §4.1 pipeline — pause, API key, CORS preflight, the 5 MB ceiling, matching, chaos,
placeholder rendering — and hands the transport a `ResponsePlan`: a status, headers, a body, how
many bytes of it to write, what `Content-Length` should claim, and whether to kill the socket
afterwards. `ResponseWriter` executes that and does no thinking of its own.

The transport is passed in as a lambda rather than the plan being returned, which keeps the one
piece of ordering anybody would get wrong — delay, then write, then log, with the duration
measured around the write but *excluding* the delay — inside the layer that can be unit-tested.

### Editing a route never restarts the engine

Routes live in an immutable `RoutingSnapshot` behind an `AtomicReference`. Saving compiles a new
one off the main thread and swaps the reference; a request reads it once at entry and resolves
entirely against that value. An in-flight request finishes under the rules it started with, the
next one sees the new rules, and no keep-alive socket is dropped.

Only the port, the bind address and the API key need a restart, and the Settings screen names
which of them is stale rather than restarting silently.

### The traffic log cannot slow the server down

`offer` pushes onto a lock-free queue and pokes a conflated channel; a coroutine on
`Dispatchers.Default` drains it into a ring buffer and publishes once per batch. On overflow the
*oldest queued* entry is dropped and counted, and the count is shown in the UI — a log that
silently loses entries is a log that quietly lies.

### Chaos is recorded, not just applied

Every traffic entry carries what chaos did. Without that, an injected 503 is indistinguishable
from a bug in the mock rules, and the hour spent working that out is an hour of believing the tool
is lying to you.

---

## 4. Testing

```bash
./gradlew testDebugUnitTest
```

171 tests, all on the JVM, split the way SPEC §14 asks:

**Unit tests over the pure-Kotlin modules** — path normalisation and pattern grammar, precedence,
matcher semantics, the scoring arithmetic and every step of the tie-break chain, deep-subset body
matching, placeholder resolution, and chaos with a fixed seed so the assertions are exact rather
than statistical.

**Ktor `testApplication`** — the wiring the unit tests cannot see: header and `Content-Length`
emission, stage ordering, the API-key guard with its `OPTIONS` exemption, CORS precedence, `413`,
`HEAD`, chaos reaching the wire, and a snapshot swap taking effect on the next request but not the
one in flight.

**Golden files** for every importer, because what breaks an importer is the shape real exports
have: Postman writing a URL two ways in one file, a HAR repeating the same request eleven times, a
spec that defines an operation and gives it no example.

Assert typed `DomainError` values, never message substrings — a substring assertion passes for the
wrong reason the moment the copy is reworded.

### Manual verification

Not automated, and each of these has caught something a JVM test could not:

- Screen-off endurance over an hour, with a client polling.
- Serving a laptop over the phone's hotspot.
- A Wi-Fi → hotspot transition while the server is running.
- mDNS resolution from macOS and Linux.
- Scanning the QR with a stock camera app.
- Killing the app from the recents screen and checking the recovery card on next launch.

---

## 5. Architecture conventions

### MVI

Every feature declares `State`, `Intent` and `Effect` in its `Contract.kt`. **State or Effect?**
Ask whether replaying it after a rotation would be wrong — a toast, a snackbar or a navigation is
an Effect; a loading flag, a dialog's visibility, list contents and text are State.

`MviViewModel` (in `:core:ui`) owns the `MutableStateFlow` and the `Channel`, exposes only `state`
and `effect`, and gives subclasses `reduce { }`, `currentState` and `emitEffect()`.

Collect effects with `ObserveAsEvents`, never `LaunchedEffect(Unit) { flow.collect { } }`. The
latter is scoped to composition, and a backgrounded screen stays composed — so a `navigate()`
fires while the app is invisible and the user returns to the wrong screen.

### Errors

Repositories and use cases return `AppResult<T>` — `Success(data)` or `Failure(DomainError)` —
never `kotlin.Result` and never a raw `Throwable`.

```
Exception ──► toDomainError() ──► DomainError ──► toMessageRes() ──► stringResource
  :core:common                      everywhere          :core:ui         the composable
```

`safeCall { }` is the boundary for repository work; it rethrows `CancellationException` **before**
catching `Throwable`, which is the line `runCatching` is missing. The request path uses
`respondCatching` instead: a throwing handler has to become a 500 on the wire plus a logged
traffic entry, not an `AppResult` nobody is holding.

`DomainError` is sealed, so a new failure mode is a compile error at every site that must handle
it. `e.message` never reaches state — it is developer text, frequently `null`, always untranslated.

---

## 6. Things that will bite you

- **Kotlin block comments nest.** `/files/**` inside a KDoc opens a nested comment and the file
  stops compiling several declarations later, with an error that points nowhere near the cause.
- **`@ShowkaseRoot` must live in `src/main`, not `src/debug`.** KSP does not scan the debug source
  set for annotations, so a root placed there compiles and silently generates nothing.
- **A `@Preview` that Showkase should catalog must be `internal`, not `private`.**
- **A type returned from a `@Provides` is part of that module's public surface**, because Hilt
  generates the component in `:app` — use `api`, not `implementation`.
- **Use `LocalResources.current`, not `LocalContext.current.getString()`.** A `Context` read is not
  invalidated by a configuration change, so text goes stale after a locale switch.
- **Give every `LazyColumn` item a stable `key`.** Without one, inserting at the top — which the
  traffic log does constantly — recomposes every row.
- **Never call `android.util.Log` directly.** Use `Logger`, whose sink is installed only in debug
  builds; a stray log line in a release build of a developer tool is likely printing somebody's
  request bodies to logcat.
