# Local API Mock Server & Network Inspector — Technical Specification

**Platform:** Android (phone, portrait only)
**Language / stack:** Kotlin · Jetpack Compose · MVI · Clean Architecture · Hilt · Ktor CIO (embedded server) · Room · DataStore
**minSdk:** 29 · **targetSdk:** current
**Distribution:** Internal team only (sideload / internal APK) — not published to Play
**Version scope:** v1 unless a section is explicitly marked *(v2)*

---

## 1. Product Summary

A portable HTTP mock server that runs on an Android phone. It binds an embedded Ktor CIO engine to a local port so that:

- Any device on the same Wi-Fi or the phone's hotspot can call `http://<phone-ip>:<port>/...`
- Other apps on the same device can call `http://localhost:<port>/...`
- The server survives app minimisation and screen lock via a foreground service

Developers use it to build client UIs before a backend exists, to trigger error states (401/429/500) on demand, and to demo client–server integrations with no network access at all.

### 1.1 Core Capabilities (v1)

| Capability | Summary |
|---|---|
| Embedded server | Ktor CIO bound to `0.0.0.0:<port>`, configurable port with auto-fallback |
| Route management | Collections of routes with base-path prefixes, method + path + matchers |
| Request matching | Most-specific-wins scoring across path, method, headers, query, JSON body subset |
| Response definition | Status, headers, static body with `{{path.x}}` / `{{query.x}}` / `{{header.x}}` placeholders |
| Traffic inspector | In-memory live log, auto-follow, filters, full-text body search |
| Chaos engine | Global profile + per-route override: delay, random error status, connection reset, malformed body |
| Import | OpenAPI 3.x, Postman v2.1, HAR 1.2 |
| Export | JSON via share sheet, JSON to Downloads, Postman v2.1 collection |
| Discovery | Ranked IP display + copy, QR code, mDNS/NSD advertisement |
| Access control | Optional API key header |

### 1.2 Explicit Non-Goals (v1)

- No traffic recording/interception of other apps (no VPNService, no proxy passthrough)
- No browser-based admin UI — all authoring happens in-app
- No HTTPS/TLS (*v2*)
- No JSONPath or regex matchers (*v2*)
- No WebSocket / SSE / binary-file responses (*v2*)
- No scenario state machines (*v2*)
- No tablet or landscape layouts
- No cloud sync, accounts, or team sharing beyond file export

---

## 2. Architecture

### 2.1 Layering

Standard Clean Architecture per the project's `android-mvi` conventions, with one deliberate twist: **the entire matching and chaos decision logic lives in the domain layer as use cases in pure Kotlin, with zero Ktor and zero Android imports.** The Ktor engine is a data-layer adapter that translates `ApplicationCall` → domain `MockRequest`, calls exactly one use case, and writes the result back out. This is what makes the riskiest logic unit-testable without spinning up a server.

There are two entry paths into the domain, and both terminate at use cases:

```
  UI PATH                              SERVER PATH
[ Compose UI ]                     [ HTTP Client on LAN / localhost ]
      │ collectAsStateWithLifecycle       │
      │ onIntent(FeatureIntent)           ▼
      ▼                            [ Ktor CIO Engine ]      ← data (framework adapter)
[ ViewModel ]                            │ KtorCallAdapter
      │ invoke()                         │ MockRequest (pure)
      ▼                                  ▼
[ UseCase(s) ]  ◀───────────── [ HandleMockRequestUseCase ] ← domain, pure Kotlin
      │ Result<T>                        │   ├─ ResolveRouteUseCase
      ▼                                  │   ├─ DecideChaosUseCase
[ Repository Interface ]  ← domain       │   ├─ ResolvePlaceholdersUseCase
      │                                  │   └─ LogTrafficUseCase
      ▼                                  │ MockResolution
[ RepositoryImpl ]  ← data               ▼
      │                            [ ResponseWriter + ChaosApplier ]  ← data
      ├── Room       (collections, routes, chaos profiles)
      ├── DataStore  (server settings)
      └── In-memory  (traffic ring buffer — TrafficRepositoryImpl)
```

**Layer rules, applied here:**

- **Domain** — pure Kotlin, no Android and no Ktor imports. Models, `MockServerException`, repository interfaces, use cases. Everything in §4, §5.1, and §6 lives here.
- **Data** — implements domain interfaces. Room entities, DAOs, DataStore, the Ktor engine and its adapters, importers/exporters, mappers.
- **Presentation** — ViewModel + Compose only. No business logic; every action goes through a use case.
- **Core** — cross-cutting: `AppConfig`, `AppConstants`, `Resource`, `Logger`, extensions.

**One documented deviation:** `MockServerEngine` sits in the data layer but depends on `HandleMockRequestUseCase` in the domain layer. The dependency still points inward, so no rule is broken — and collapsing the per-request pipeline into a single use case keeps the data layer's coupling surface to exactly one domain type instead of four.

### 2.2 Server Ownership

The Ktor engine lives in a **Hilt `@Singleton` (`MockServerEngine`)**, not in the service and not in a ViewModel.

- `MockServerService` is a foreground service whose only job is to keep the process alive, own the notification, and drive start/stop.
- The engine is never touched directly by the presentation layer. `MockServerRepository` (domain interface) is implemented by `MockServerRepositoryImpl` (data) which delegates to the engine, so ViewModels only ever see `StartServerUseCase`, `StopServerUseCase`, and `ObserveServerStatusUseCase`. No service binding, no AIDL, no cross-process IPC.
- Single process. No `:server` process — a second process would force IPC for every state read and duplicate the Hilt graph for no benefit.

**Consequence to respect:** if the app's process dies, the engine dies with it. That is why the foreground service exists, and why the service returns `START_STICKY` and re-binds using the last-running config on system restart, showing a "server restarted after being killed" notification.

```kotlin
// data/server/MockServerEngine.kt
@Singleton
class MockServerEngine @Inject constructor(
    private val handleMockRequest: HandleMockRequestUseCase,   // domain — the only decision point
    private val callAdapter: KtorCallAdapter,                  // ApplicationCall → MockRequest
    private val responseWriter: ResponseWriter,                // MockResolution → ApplicationCall
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _status = MutableStateFlow<ServerStatus>(ServerStatus.Stopped)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()   // never expose the mutable flow

    // Immutable snapshot swapped atomically on save — see §5.5
    private val snapshot = AtomicReference(RoutingSnapshot.EMPTY)
}

// domain/repository/MockServerRepository.kt — domain defines, data implements
interface MockServerRepository {
    val status: StateFlow<ServerStatus>
    suspend fun start(config: ServerConfig): Result<BoundAddress>
    suspend fun stop(): Result<Unit>
    suspend fun applySnapshot(snapshot: RoutingSnapshot)
}
```

### 2.3 Package Structure

```
com.rio.mockserver/
├── core/
│   ├── config/
│   │   ├── AppConfig.kt              # Reads BuildConfig — default port, log cap, body caps
│   │   └── AppConstants.kt           # Server, Matching, Chaos, Traffic, Import constant objects
│   └── util/
│       ├── Resource.kt               # Success / Error / Loading
│       ├── Logger.kt                 # Debug-gated; never call Log.d directly
│       ├── FlowExt.kt                # collectAsEffect()
│       ├── ContextExt.kt             # showToast(), copyToClipboard(), shareText()
│       ├── StringExt.kt              # orDash(), normalisePath(), toTitleCase()
│       ├── DateUtil.kt               # timestamp formatting for traffic rows
│       ├── JsonFormatter.kt          # prettify / validate / highlight spans
│       └── NetworkInterfaceUtil.kt   # interface enumeration + ranking (§8.5)
├── di/
│   ├── DatabaseModule.kt             # AppDatabase, DAO singletons
│   ├── DataStoreModule.kt            # Preferences DataStore
│   ├── ServerModule.kt               # MockServerEngine, KtorCallAdapter, ResponseWriter, NsdAdvertiser
│   ├── DispatcherModule.kt           # @IoDispatcher / @DefaultDispatcher qualifiers
│   └── RepositoryModule.kt           # @Binds interface → impl
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/                      CollectionDao, RouteDao, ChaosProfileDao
│   │   ├── entity/                   CollectionEntity, RouteEntity, ChaosProfileEntity
│   │   └── converter/                MatcherListConverter, HeaderMapConverter, ResponseConverter
│   ├── preferences/
│   │   └── ServerSettingsDataStore.kt
│   ├── server/                       # framework adapter — the only package that imports Ktor
│   │   ├── MockServerEngine.kt
│   │   ├── KtorCallAdapter.kt        # ApplicationCall → MockRequest
│   │   ├── ResponseWriter.kt         # MockResolution → ApplicationCall
│   │   ├── ChaosApplier.kt           # delay / reset / truncate at the socket level
│   │   ├── ApiKeyGuard.kt
│   │   ├── CorsHandler.kt
│   │   └── MockServerService.kt      # foreground service (specialUse) + notification
│   ├── discovery/                    NsdAdvertiser.kt, QrEncoder.kt
│   ├── importer/                     OpenApiImporter.kt, PostmanImporter.kt, HarImporter.kt
│   ├── exporter/                     JsonExporter.kt, PostmanExporter.kt, HarExporter.kt, CurlFormatter.kt
│   ├── dto/                          OpenApiDto.kt, PostmanDto.kt, HarDto.kt, CollectionExportDto.kt
│   ├── mapper/                       RouteMapper.kt, CollectionMapper.kt, ChaosMapper.kt,
│   │                                 OpenApiMapper.kt, PostmanMapper.kt, HarMapper.kt
│   └── repository/                   CollectionRepositoryImpl, RouteRepositoryImpl,
│                                     TrafficRepositoryImpl, ChaosRepositoryImpl,
│                                     ServerSettingsRepositoryImpl, MockServerRepositoryImpl
├── domain/
│   ├── model/                        MockCollection, MockRoute, RequestMatcher, MockResponse,
│   │                                 ChaosProfile, ChaosApplied, MockRequest, MockResolution,
│   │                                 RoutingSnapshot, PathPattern, MatchScore, TrafficEntry,
│   │                                 ServerConfig, ServerStatus, BoundAddress, CorsConfig
│   ├── exception/                    MockServerException.kt  # PortUnavailable, BindFailed,
│   │                                                         # InvalidPathPattern, ImportParseError
│   ├── repository/                   CollectionRepository, RouteRepository, TrafficRepository,
│   │                                 ChaosRepository, ServerSettingsRepository, MockServerRepository
│   └── usecase/
│       ├── server/                   StartServerUseCase, StopServerUseCase,
│       │                             ObserveServerStatusUseCase, ObserveServerAddressesUseCase,
│       │                             SetPauseModeUseCase
│       ├── request/                  HandleMockRequestUseCase, ResolveRouteUseCase,
│       │                             DecideChaosUseCase, ResolvePlaceholdersUseCase,
│       │                             LogTrafficUseCase,
│       │                             MatchScorer.kt, BodySubsetMatcher.kt   # package-private collaborators
│       ├── collection/               GetCollectionsUseCase, SaveCollectionUseCase,
│       │                             DeleteCollectionUseCase, ToggleCollectionUseCase
│       ├── route/                    GetRoutesUseCase, SaveRouteUseCase, DeleteRouteUseCase,
│       │                             DuplicateRouteUseCase, DetectShadowedRoutesUseCase,
│       │                             CreateRouteFromTrafficUseCase, ValidateRouteUseCase
│       ├── traffic/                  ObserveTrafficUseCase, ClearTrafficUseCase,
│       │                             ExportTrafficAsHarUseCase, FormatEntryAsCurlUseCase
│       ├── chaos/                    GetChaosProfilesUseCase, SaveChaosProfileUseCase,
│       │                             SetGlobalChaosProfileUseCase, ResetChaosSeedUseCase
│       ├── importexport/             ImportOpenApiUseCase, ImportPostmanUseCase, ImportHarUseCase,
│       │                             ExportCollectionJsonUseCase, ExportPostmanCollectionUseCase
│       └── settings/                 GetServerSettingsUseCase, UpdateServerSettingsUseCase,
│                                     GenerateApiKeyUseCase
└── presentation/
    ├── navigation/                   AppNavHost.kt, MockDestination.kt, BottomNavItem.kt
    ├── components/                   JsonEditor.kt, MethodBadge.kt, StatusPill.kt,
    │                                 AddressBlock.kt, EmptyState.kt, ChaosBanner.kt
    ├── server/                       ServerContract.kt, ServerViewModel.kt, ServerScreen.kt
    ├── collection/                   CollectionContract.kt, CollectionViewModel.kt, CollectionScreen.kt
    ├── collectiondetail/             CollectionDetailContract.kt, ...ViewModel.kt, ...Screen.kt
    ├── routeeditor/                  RouteEditorContract.kt, RouteEditorViewModel.kt, RouteEditorScreen.kt
    ├── traffic/                      TrafficContract.kt, TrafficViewModel.kt, TrafficScreen.kt
    ├── trafficdetail/                TrafficDetailContract.kt, ...ViewModel.kt, ...Screen.kt
    ├── chaos/                        ChaosContract.kt, ChaosViewModel.kt, ChaosScreen.kt
    ├── importexport/                 ImportExportContract.kt, ...ViewModel.kt, ...Screen.kt
    └── settings/                     SettingsContract.kt, SettingsViewModel.kt, SettingsScreen.kt
```

**Notes on conformance:**

- There is no `NetworkModule` and no `data/remote/` — the app makes no outbound HTTP calls. `ServerModule` and `data/server/` are their structural counterparts: the same framework boundary, with traffic flowing inbound instead of outbound. `safeApiCall` has no analogue; the equivalent boundary wrapper is `respondCatching` inside `ResponseWriter`, which converts any handler throwable into a 500 plus a logged traffic entry rather than a dropped socket.
- `data/dto/` holds import/export wire formats (OpenAPI, Postman, HAR, native export), mapped into domain models by `data/mapper/` — same DTO → mapper → domain discipline the skill applies to API responses.
- Every feature package holds exactly three files: `<Feature>Contract.kt`, `<Feature>ViewModel.kt`, `<Feature>Screen.kt`. `presentation/components/` is the one addition, for composables shared across features (the JSON editor is used by the route editor, the chaos editor, and the unmatched-response setting).
- `MockServerService` lives in `data/server/` rather than a top-level `service/` package, because it is a framework detail of running the engine, not a feature.

### 2.4 MVI Contracts

Each feature defines State, Intent, and SideEffect in its `Contract.kt`. The non-obvious ones:

| Feature | State highlights | Key Intents | SideEffects |
|---|---|---|---|
| `server` | `status`, `addresses`, `boundPort`, `requestCount`, `uptime`, `chaosActive`, `apiKeyEnabled` | `StartServer`, `StopServer`, `TogglePause`, `CopyAddress`, `ShowQr` | `ShowToast`, `RequestNotificationPermission`, `ShowPortFallbackDialog`, `NavigateToSettings` |
| `routeeditor` | `route`, `bodyText`, `jsonError`, `specificityScore`, `isShadowed`, `unsavedChanges` | `UpdateBody`, `PasteFromClipboard`, `PrettifyBody`, `AddMatcher`, `TestRoute`, `Save` | `NavigateBack`, `ShowUnsavedDialog`, `NavigateToTrafficEntry`, `ShowToast` |
| `traffic` | `entries`, `filters`, `searchQuery`, `isFollowing`, `newEntryCount`, `droppedCount` | `UpdateSearch`, `ToggleFilter`, `PauseFollow`, `ResumeFollow`, `ExportHar`, `CreateRuleFrom` | `NavigateToRouteEditor`, `ShareFile`, `ShowToast` |
| `importexport` | `parsedPreview`, `selectedRoutes`, `conflicts`, `targetCollectionId`, `isParsing` | `PickFile`, `ToggleRoute`, `ResolveConflict`, `Commit` | `OpenFilePicker`, `ShareFile`, `NavigateToCollection`, `ShowToast` |

Standard rules from the skill apply throughout: `collectAsStateWithLifecycle()`, `Channel.BUFFERED` + `receiveAsFlow()` for side effects, `_state.update { }` for every mutation, `Result<T>` returned from every use case, no `MutableStateFlow` ever exposed, and each screen split into a connected composable (owns the ViewModel and the side-effect collector) plus a stateless `*Content` composable that takes `state` and `onIntent`.

---

## 3. Domain Model

```kotlin
data class MockCollection(
    val id: String,                    // UUID
    val name: String,
    val basePath: String,              // "/api/v1" — normalised: leading slash, no trailing slash
    val isEnabled: Boolean,
    val order: Int,                    // tie-break priority; lower wins
    val cors: CorsConfig,
    val createdAt: Long
)

data class MockRoute(
    val id: String,
    val collectionId: String,
    val name: String,
    val method: HttpMethodFilter,      // GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS/ANY
    val pathPattern: String,           // "/users/{id}" or "/files/**"
    val matchers: List<RequestMatcher>,
    val response: MockResponse,
    val chaosOverride: ChaosProfile?,  // null = inherit global
    val isEnabled: Boolean,
    val createdAt: Long
)

sealed interface RequestMatcher {
    data class Header(val name: String, val op: StringOp, val value: String) : RequestMatcher
    data class Query(val name: String, val op: StringOp, val value: String) : RequestMatcher
    data class BodySubset(val json: String) : RequestMatcher          // v1
    // v2: data class BodyJsonPath(...), data class BodyRegex(...)
}

enum class StringOp { EQUALS, CONTAINS, EXISTS, ABSENT }

data class MockResponse(
    val statusCode: Int,
    val headers: Map<String, String>,   // Content-Type defaults to application/json
    val body: String,                   // may contain {{path.x}} {{query.x}} {{header.x}}
    val delayMillisOverride: Long?      // convenience: fixed per-route delay
)

data class ChaosProfile(
    val id: String,
    val name: String,
    val delay: DelaySpec,               // None | Fixed(ms) | Random(minMs, maxMs)
    val errorInjection: ErrorInjection, // probability + weighted status pool
    val connectionReset: FailureSpec,   // probability, dropAfterBytes
    val malformedBody: FailureSpec      // probability, truncateAtPercent
)

data class MockRequest(                 // pure — built by KtorCallAdapter
    val method: String,
    val path: String,                   // decoded, normalised (no trailing slash)
    val query: Map<String, List<String>>,
    val headers: Map<String, List<String>>,   // lookup is case-insensitive
    val body: String?,                  // null if binary or over cap
    val contentType: String?,
    val clientIp: String,
    val receivedAt: Long
)

sealed interface MockResolution {
    data class Matched(val route: MockRoute, val score: Int) : MockResolution
    data class Unmatched(val fallback: MockResponse) : MockResolution
    data object Unauthorized : MockResolution        // API key guard
    data class Paused(val retryAfterSeconds: Int) : MockResolution
}
```

---

## 4. Request Matching

### 4.1 Pipeline

For every incoming call, in order:

1. **Pause check** — if pause mode is on, return the configured 503 immediately (still logged).
2. **API key guard** — if enabled and the header is missing/wrong, return 401. `OPTIONS` preflights are exempt.
3. **CORS preflight** — if the request is `OPTIONS` and the target collection has CORS enabled, answer the preflight and stop.
4. **Candidate filter** — enabled routes in enabled collections whose `basePath + pathPattern` structurally matches the request path and whose method filter accepts the method.
5. **Matcher evaluation** — every matcher on a candidate must pass. One failure eliminates the candidate. Matchers are AND-ed; there is no OR in v1.
6. **Scoring** — highest score wins (§4.3).
7. **Unmatched** — fall through to the configurable default response (§4.5).

### 4.2 Path Pattern Syntax

| Token | Meaning | Example |
|---|---|---|
| literal segment | exact, case-sensitive | `/users` |
| `{name}` | single-segment capture, exposed as `{{path.name}}` | `/users/{id}` |
| `**` | tail wildcard, matches one or more remaining segments | `/files/**` |

Normalisation rules applied to both patterns and incoming paths before matching:

- Percent-decoding is applied once, then segments are compared decoded.
- A single trailing slash is stripped (`/users/` ≡ `/users`).
- Empty path becomes `/`.
- Duplicate slashes collapse (`//users` → `/users`).
- Query string is never part of path matching.

### 4.3 Specificity Scoring

Deterministic integer scoring. Higher wins.

| Element | Points |
|---|---|
| Literal path segment | +10 each |
| `{param}` segment | +4 each |
| `**` tail | +1 (once) |
| Explicit method (not `ANY`) | +5 |
| Header matcher | +3 each |
| Query matcher | +3 each |
| Body subset matcher | +6 each |

The intent: a route with more concrete path segments always beats a wildcard; among equally-shaped paths, a route that additionally constrains the body or headers beats a looser one.

**Tie-break chain** (applied in order until resolved):

1. Higher matcher count
2. More path segments
3. Lower `collection.order`
4. Older `route.createdAt`
5. Lexicographic `route.id` — guarantees total ordering, so resolution is never nondeterministic

The UI surfaces this: the route editor shows a live "Specificity: 34" badge, and if a saved route can never win because a strictly-more-specific route shadows it for every possible request, the list row shows a **Shadowed** warning chip.

### 4.4 Body Subset Matching (v1)

The matcher JSON is treated as a **deep subset assertion** against the parsed request body:

- Objects: every key in the matcher must exist in the request with a matching value; extra keys in the request are ignored.
- Arrays: the matcher array must be a subsequence-by-containment — each matcher element must have at least one matching element in the request array. Order is not significant.
- Scalars: strict equality, type-sensitive (`"1"` ≠ `1`).
- `null` in the matcher means "key present with null value". Use an `ABSENT` header/query matcher shape for absence assertions on bodies only in *v2*.
- If the request body is not valid JSON, any body matcher fails (candidate eliminated, never an error).

*(v2)* `BodyJsonPath` for comparisons and existence checks, `BodyRegex` for non-JSON content types.

### 4.5 Unmatched Requests

Two mechanisms, both active:

**Configurable default response** — a global setting: status (default 404), headers, and body (default `{"error":"No mock route matched","path":"{{path}}","method":"{{method}}"}`). Placeholders are resolved against the actual request so the client sees what it asked for.

**Create rule from this request** — every unmatched entry in the traffic log carries a prominent **Create rule** action. Tapping it opens the route editor pre-filled with:

- Method and path (path segments that look like IDs — numeric or UUID-shaped — are offered as `{param}` with a one-tap toggle)
- The collection whose base path is the longest prefix of the request path, or a new collection if none fits
- Content-Type header from the request
- A response body skeleton: `{}` with the status defaulted to 200

This is the deliberate replacement for traffic recording: point your client at the phone, let it fail, then build the mocks from the failures.

---

## 5. Response Generation

### 5.1 Placeholders

Only three sources in v1 — no fakers, no scripting, no randomness:

| Placeholder | Resolves to |
|---|---|
| `{{path.id}}` | Named path capture from the pattern |
| `{{query.page}}` | First value of that query parameter |
| `{{header.Authorization}}` | First value of that header (case-insensitive lookup) |
| `{{path}}` / `{{method}}` | Whole request path / method — for the default fallback body |

Rules:

- An unresolvable placeholder renders as an **empty string**, and the traffic entry is flagged `unresolvedPlaceholders: [...]` and shown with a warning chip in the log detail. Failing the request would be worse: a mock server that 500s because you typo'd a placeholder is hostile.
- `\{{` escapes a literal `{{`.
- Placeholder substitution is **string-level, post-serialisation**, so injecting a value containing `"` can produce invalid JSON. The editor warns when a placeholder sits inside a JSON string literal without quotes around it, but does not block saving.
- Substitution happens on the body and on header *values*, not on header names or status codes.

### 5.2 Headers

- `Content-Type` defaults to `application/json; charset=utf-8` when not specified.
- `Content-Length` is always computed by the writer and cannot be manually set (except when chaos truncation is active — see §6).
- `Date`, `Server`, `Connection` are managed by Ktor; user-supplied values for these are ignored with an editor warning.
- CORS headers are injected by the CORS handler after user headers, and win on conflict.

### 5.3 HEAD and OPTIONS

- `HEAD` matches routes declared as `GET` or `ANY` and returns headers with an empty body and correct `Content-Length`.
- `OPTIONS` is consumed by the CORS handler when the collection has CORS enabled; otherwise it goes through normal matching.

### 5.4 Request Body Handling

- Requests up to **5 MB** are read fully into memory for matching and logging.
- Larger requests are rejected with `413` and logged with `body = null, truncated = true`. This ceiling protects a phone from a client that decides to upload a video to a mock endpoint.
- Non-text content types (`image/*`, `application/octet-stream`, `multipart/*`) are not stored as text; the log shows `<binary, N bytes>`. Body matchers against them always fail.
- `gzip`/`deflate` request bodies are decompressed before matching.

### 5.5 Hot-Apply Semantics

Editing a route while the server is running **never restarts the engine**. Restarting would drop every keep-alive socket and break clients mid-test.

Implementation: routes are held in an immutable `RoutingSnapshot` behind an `AtomicReference`. Saving builds a new snapshot off the main thread and swaps the reference in one operation. Each incoming request captures the snapshot once at entry and resolves entirely against that snapshot — so an in-flight request completes against the rules it started with, and the next request sees the new rules. No locks in the request path, no torn reads.

Only these changes require an engine restart, and each prompts explicitly ("Restart server to apply?"):

- Port change
- API key enable/disable
- Bind-address change

---

## 6. Chaos & Latency Engine

### 6.1 Scope Resolution

`route.chaosOverride ?: globalChaosProfile`. A route override replaces the global profile wholesale — it does not merge field-by-field. Merging is the kind of behaviour that produces "why is this endpoint sleeping 3 seconds" bug hunts.

The server screen shows a persistent **Chaos: ON** banner when any non-empty profile is active globally, and route rows show a chaos icon when overridden.

### 6.2 Failure Modes

Applied in this order for each request:

1. **Delay** — `None`, `Fixed(ms)`, or `Random(min..max)`. Implemented as `delay()` inside the call handler, so it consumes no thread. Capped at 60 s with an editor warning above 30 s.
2. **Error status injection** — probability `p`, drawn from a weighted pool of statuses (defaults: 500 / 503 / 429, equal weight, all configurable). The injected response body is configurable per profile; `429` responses include a `Retry-After` header.
3. **Connection reset** — probability `p`. Implemented by writing `dropAfterBytes` of the body to the response channel, flushing, then cancelling the channel with an exception so the socket closes without a clean end. The client sees a reset / premature EOF. `dropAfterBytes = 0` closes before any body is written.
4. **Malformed body** — probability `p`. Sends a correct status and headers, then a body truncated at `truncateAtPercent` of its length, with `Content-Length` deliberately declaring the *full* length. Clients see a JSON parse error or an incomplete-body error depending on how strict they are.

Only one failure mode fires per request. They are evaluated in the order above; the first one that triggers wins, and the rest are skipped.

### 6.3 Determinism

The chaos RNG is a seeded `Random` held by `DecideChaosUseCase`. The seed is exposed in settings and regenerated on demand. This makes "reproduce that flaky failure" possible and makes the chaos unit tests deterministic.

Every traffic entry records what chaos did: `chaosApplied: DelayApplied(2400) | ErrorInjected(503) | ConnectionReset(128) | MalformedBody(60%) | null`. Without this, an injected 503 is indistinguishable from a bug in the mock rules, which would quietly destroy trust in the tool.

---

## 7. Traffic Inspector

### 7.1 Storage Model

**Nothing about traffic touches Room.** Logs are an in-memory ring buffer, cleared on process death. Rationale: sustained Room writes on the request path would add disk I/O to every response, the data has a lifespan of one debugging session, and a phone should not accumulate megabytes of stranger's request bodies.

- Capacity: **configurable, default 500 entries**. Oldest drops first.
- Body cap: **256 KB per direction**, truncated with `truncated = true` and a UI chip.
- Implementation: `ArrayDeque<TrafficEntry>` guarded by a `Mutex`, published through a `StateFlow<List<TrafficEntry>>`.
- **Backpressure:** the request path never blocks on logging. `LogTrafficUseCase` offers into a `Channel(capacity = 256, onBufferOverflow = DROP_OLDEST)` owned by `TrafficRepositoryImpl`; a dedicated coroutine on `Dispatchers.Default` drains it into the buffer. If logging can't keep up, the log loses entries — the server does not lose throughput. A dropped-entry counter is shown in the UI so the loss is visible rather than silent.

### 7.2 Entry Contents

```kotlin
data class TrafficEntry(
    val id: String,
    val receivedAt: Long,
    val clientIp: String,
    val method: String,
    val path: String,
    val query: Map<String, List<String>>,
    val requestHeaders: Map<String, List<String>>,
    val requestBody: String?,
    val requestTruncated: Boolean,
    val matchedRouteId: String?,        // null = unmatched
    val matchedRouteName: String?,
    val matchScore: Int?,
    val statusCode: Int,
    val responseHeaders: Map<String, String>,
    val responseBody: String?,
    val responseTruncated: Boolean,
    val durationMillis: Long,           // excludes chaos delay
    val chaosApplied: ChaosApplied?,
    val unresolvedPlaceholders: List<String>
)
```

`durationMillis` deliberately excludes injected delay, with the delay shown separately — otherwise every latency figure becomes a lie the moment chaos is on.

### 7.3 Log UI

- **Auto-follow with pause-on-scroll.** New entries stream in at the top; touching the list to scroll pauses following and shows a "N new — jump to latest" pill. Releasing at the top resumes.
- **Filters:** method chips, status class chips (2xx/3xx/4xx/5xx), matched/unmatched toggle, collection selector.
- **Full-text search** across path, headers, request body, response body. Debounced 300 ms, filtering the in-memory list — no index needed at 500 entries.
- Row layout: method badge · path (middle-ellipsised) · status pill · duration · chaos icon · unmatched icon.
- Detail screen: tabbed Request / Response, with pretty-printed JSON and header tables.

### 7.4 Per-Entry Actions

| Action | Behaviour |
|---|---|
| Copy as cURL | Full reproduction: method, URL with query, headers, `--data` body. Uses the currently-displayed server address. |
| Copy body | Raw body to clipboard (pretty or raw toggle) |
| Share as text | Share sheet with a formatted request/response transcript |
| Create rule from this | §4.5 |

**Export session as HAR** is a screen-level action: the current filtered set is serialised to HAR 1.2 and saved/shared, so it can be opened in Charles, Fiddler, or a browser devtools importer.

---

## 8. Server Lifecycle & Android Platform

### 8.1 Foreground Service

- Type: **`specialUse`**, declared with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` = "Local HTTP mock server for developer testing; must remain reachable while the screen is off." Chosen over `dataSync`, which on Android 15+ has a cumulative ~6-hour daily runtime budget that would kill an all-day mock session. Internal distribution means the justification is never reviewed, but the manifest is correct either way.
- Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE` (NSD).
- Notification (ongoing, low importance, silent): title = server URL, text = live request counter, actions = **Stop** and **Pause**. The counter update is rate-limited to **1 Hz** — a per-request notification update would stutter the system UI under load.

### 8.2 Radio & Doze

- **`WifiLock` in `WIFI_MODE_FULL_LOW_LATENCY`**, acquired on start and released on stop. Without it the Wi-Fi radio parks with the screen off and connections stall or drop.
- **No `WakeLock`.** The foreground service already exempts the app from most Doze network restrictions, and a partial wakelock would drain the battery for no measurable benefit.
- **Battery-optimisation exemption is contextual, never on first launch.** It's offered in Settings, and surfaced automatically as a dismissible card if the app detects it was killed while the server was running (a persisted "was running" flag that survives to next launch without a clean stop).

### 8.3 Startup Behaviour

Manual start only. No auto-start on app launch, no boot receiver. The last-used config is restored into the UI, but the socket does not open until the user taps Start. A dev tool that silently opens a LAN port every time the phone boots is a bad citizen.

### 8.4 Port Handling

- Default **8080**, user-configurable. Ports below 1024 are rejected in the editor (they require root on Linux/Android).
- **Auto-fallback:** if the chosen port is taken (`BindException`), try `port+1 … port+10`. On success, the UI shows "Port 8080 was in use — running on 8081" and offers to remember the new port. If all 11 fail, the server stays stopped with an actionable error.
- Bind address is `0.0.0.0` so both LAN clients and `localhost` callers are served by one socket.

### 8.5 Address Discovery

`NetworkInterfaceUtil` enumerates interfaces, drops loopback and down interfaces, then ranks:

1. `wlan*` — Wi-Fi (usual case)
2. `ap*` / `swlan*` — hotspot (`192.168.43.x` family)
3. `rndis*` — USB tethering
4. everything else
5. `tun*` — VPN, always last: LAN clients cannot route to it

The server screen shows the winner large with a copy button, a **"N more addresses"** expander for the rest, and always a separate `http://localhost:<port>` row for on-device IPC. The address list re-resolves on `ConnectivityManager.NetworkCallback` events, because the IP changes the instant Wi-Fi flips or a hotspot comes up. With no usable interface, the UI shows an explicit **"No network — localhost only"** state rather than a blank field.

**QR code:** encodes the plain base URL (`http://192.168.1.45:8080`), generated with ZXing core (encode only — no camera dependency, no scanner). Shown in a full-screen dialog with brightness boosted.

**mDNS/NSD:** registers `_http._tcp` as `MockServer-<device-model>` via `NsdManager` on start, unregisters on stop. On name collision Android appends a suffix, which the UI reflects. Advertised name is shown next to the IP.

### 8.6 Access Control

- **Optional API key.** When enabled, every mock route requires a header (default name `X-Mock-Key`, configurable; value generated as a random 16-char token, editable). Failures return `401` with a JSON error and are logged with `matchedRouteId = null` and a distinct **Rejected** chip.
- `OPTIONS` preflights bypass the guard, since browsers won't send custom headers on a preflight.
- The server screen carries a permanent, non-dismissible note when the key is off: **"Anyone on this network can reach these endpoints."** No fake security theatre, just an honest statement.

### 8.7 CORS

Per-collection config: allowed origins (default `*`), allowed methods, allowed headers (default `*`), `allowCredentials` (default false), `maxAge` (default 3600).

Guard rail: `allowCredentials = true` combined with origin `*` is invalid per spec and browsers reject it. The editor blocks that combination with an inline explanation rather than letting a user spend an afternoon on it.

---

## 9. Import & Export

### 9.1 Import

| Format | Version | Notes |
|---|---|---|
| OpenAPI | 3.0 / 3.1, JSON + YAML | Paths, methods, and response bodies |
| Postman | Collection v2.1 | Requests + saved example responses |
| HAR | 1.2 | Entries become routes with real captured responses |

**OpenAPI body policy — spec examples only.** Bodies come from `example` / `examples` on the response media type, falling back to a schema-level `example` if present. **Schemas are never synthesised into fake payloads**, because a generated body full of `"string"` and `0` looks real but tests nothing. Routes imported without an example are created with an empty body and flagged **Needs body** — an amber chip in the route list plus an "N routes need bodies" banner on the collection, so the gap is visible instead of silently shipping `{}`.

Response selection: the lowest 2xx status defined for the operation becomes the route's response. Other statuses are not imported as separate routes in v1 (they'd all match identically and shadow one another) — they're offered in the editor as one-tap alternates.

Parsing uses `kotlinx.serialization` for JSON and `kaml` for YAML, with a hand-rolled traversal of just the fields needed. `swagger-parser` is deliberately avoided: it's a large Java dependency with reflection and file-system assumptions that don't sit well in an APK.

**Import flow:** pick file (SAF) → parse → preview screen listing every route to be created, with per-route checkboxes and a target collection selector → conflict resolution for routes matching an existing method+path (**Skip / Replace / Keep both**) → commit. Parse failures show the offending JSON pointer or line, not a stack trace.

### 9.2 Export

- **Native JSON** — full fidelity (collections, routes, matchers, chaos overrides, CORS). Versioned with `schemaVersion` so future imports can migrate. Available via **share sheet** and **save to Downloads** (SAF `CREATE_DOCUMENT`).
- **Postman v2.1 collection** — lossy by nature; matchers and chaos have no Postman equivalent and are dropped with a pre-export warning listing exactly what won't survive.
- **HAR 1.2** — traffic log export, §7.4.

---

## 10. UI / UX

### 10.1 Navigation

Single activity, Compose Navigation, bottom bar with four destinations:

| Tab | Purpose |
|---|---|
| **Server** | Start/stop, address block, QR, port, status, chaos banner, live request counter |
| **Routes** | Collection list → route list → route editor |
| **Traffic** | Live log, filters, search, detail |
| **Settings** | Port, API key, chaos global profile, log cap, import/export, battery optimisation, RNG seed |

Full-screen (non-tab) destinations: Route Editor, Traffic Detail, Import Preview, QR display, Chaos Profile Editor.

### 10.2 Route Editor — the make-or-break screen

Phone authoring lives or dies here. Structure: collapsible sections in a single scroll (Request · Matchers · Response · Chaos), not a wizard, so the whole rule stays reviewable.

The body field is the critical component:

- **Paste from clipboard is the primary action** — a large, prominent button above the field, not a keyboard long-press. If the clipboard holds valid JSON on screen entry, the button reads "Paste JSON from clipboard" with a preview of the first line.
- **Format / prettify button** — reindents in place; also usable as a validity check.
- **Inline validation** — parse on a 500 ms debounce, error message with line/column beneath the field, and a red gutter marker on the offending line. Invalid JSON blocks Save *only* when Content-Type is `application/json`; other content types save freely.
- **Monospace with syntax highlighting** — keys, strings, numbers, literals coloured via an `AnnotatedString` transform over the theme palette. No third-party editor library.
- Auto-closing brackets and quotes, and a mini toolbar row above the keyboard with `{ } [ ] " : ,` — the characters buried three taps deep on a phone keyboard.

Other editor affordances: method as a segmented chip row; path field with a live-preview line showing the full resolved URL (`http://192.168.1.45:8080/api/v1/users/{id}`); status code as a searchable picker with common codes pinned (200, 201, 204, 400, 401, 403, 404, 409, 422, 429, 500, 503); a live **Specificity: N** badge; and a **Test this route** button that fires a loopback request through the running server and drops you at the resulting traffic entry.

### 10.3 Server Screen

Address block is the hero element: large monospace URL, copy button, QR button, expander for other interfaces, and `localhost` row. Start/stop is a single large button with a clear state colour. Below it: uptime, total requests, requests/min, unmatched count, and the chaos banner if active.

### 10.4 Visual System

Material 3, dynamic colour where available with a fixed fallback palette, dark theme default (this is a tool used in dark rooms next to a laptop). All strings in `strings.xml` from day one, all dimensions in `Dimens.kt`, all colours from the theme — no hardcoded values, per the project's code-review skill.

---

## 11. Persistence

### 11.1 Room

Database `mock_server.db`, version 1. Stores configuration only, never traffic.

| Table | Columns of note |
|---|---|
| `collections` | id (PK), name, basePath, isEnabled, order, corsJson, createdAt |
| `routes` | id (PK), collectionId (FK, CASCADE), name, method, pathPattern, matchersJson, responseJson, chaosOverrideJson, isEnabled, createdAt |
| `chaos_profiles` | id (PK), name, delayJson, errorInjectionJson, connectionResetJson, malformedBodyJson |

Complex nested values are stored as JSON via `TypeConverter` (kotlinx.serialization). Justification: matchers and responses are always read and written as a whole route, never queried field-wise, so normalising them into child tables would buy joins and migration pain with no query benefit.

Indices on `routes.collectionId` and `routes.isEnabled`. Migrations are written explicitly from v1 onward — `fallbackToDestructiveMigration` is never used, since losing a hand-built mock collection is a real cost.

### 11.2 DataStore (Preferences)

Server settings: port, lastBoundPort, apiKeyEnabled, apiKeyHeaderName, apiKeyValue, globalChaosProfileId, chaosSeed, logCapacity, defaultUnmatchedResponseJson, pauseStatusCode, wasRunningFlag, batteryPromptDismissedAt.

---

## 12. Concurrency & Performance

- The engine owns a `CoroutineScope(SupervisorJob() + Dispatchers.IO)` and is entirely independent of any `viewModelScope`.
- Ktor CIO handles connections on its own dispatcher; handlers are suspending and never block. Injected delays use `delay()`, so a 30-second chaos delay costs no thread.
- The request path performs **zero disk I/O**: routes come from the in-memory snapshot, logging is a non-blocking channel offer.
- Snapshot rebuild happens on `Dispatchers.Default` and swaps atomically (§5.5).
- Targets on a mid-range device: **p50 < 15 ms** and **p95 < 40 ms** for a static 5 KB JSON body with chaos off; sustained **50 req/s** with no dropped log entries; steady-state memory under 120 MB with a full 500-entry log.

---

## 13. Edge Cases

| Case | Handling |
|---|---|
| Port already in use | Auto-fallback `port+1…+10`, then actionable error (§8.4) |
| Port < 1024 | Rejected at input with explanation |
| Wi-Fi drops mid-session | Socket bound to `0.0.0.0` survives; address list re-resolves; UI shows "No network — localhost only" if all interfaces vanish |
| IP changes (Wi-Fi → hotspot) | New address shown automatically via NetworkCallback; QR regenerates |
| Airplane mode | Server keeps serving `localhost`; LAN row shows unavailable |
| VPN active | `tun*` listed last with a note that LAN clients can't reach it |
| Process killed by system | `START_STICKY` restart re-binds last config; notification states it restarted |
| Battery optimisation kills service | Detected via `wasRunning` flag on next launch → exemption card |
| Request > 5 MB | `413`, logged with truncation flag |
| Binary request body | Not stored as text; shown as `<binary, N bytes>`; body matchers fail |
| Malformed JSON request body | Body matchers fail (candidate eliminated), request still matched by path/method routes |
| Two routes with identical shape | Total ordering via tie-break chain (§4.3); loser shows **Shadowed** chip |
| Placeholder can't resolve | Empty string + warning flag on the traffic entry |
| Placeholder breaks JSON validity | Rendered as-is; editor warns at authoring time |
| Trailing slash / duplicate slashes / percent-encoding | Normalised before matching (§4.2) |
| Repeated query params (`?id=1&id=2`) | Stored as a list; `EQUALS` matches if any value matches; `{{query.id}}` resolves to the first |
| Header case (`content-type` vs `Content-Type`) | Case-insensitive lookup and matching throughout |
| Client sends no `Host` header (HTTP/1.0) | Served normally; matching never depends on Host |
| Keep-alive connections during rule edits | Unaffected — hot-apply never restarts the engine (§5.5) |
| Chaos reset fires on a keep-alive connection | Connection closed; client reconnects for the next request |
| Log buffer overflow under load | Oldest entries dropped, dropped-count surfaced in UI |
| Import file is huge / malformed | Parsed off the main thread with a cancellable progress state; errors point to a location, not a stack trace |
| Duplicate route on import | Skip / Replace / Keep both, per route |
| Notification permission denied (13+) | Server still runs; in-app banner explains the service is less protected and links to settings |

---

## 14. Testing Strategy

**Unit tests — matcher and chaos (the highest-risk logic, and pure Kotlin by design):**

- Table-driven `ResolveRouteUseCase` cases: literal vs param vs wildcard precedence, method filters, matcher AND semantics, scoring arithmetic, every step of the tie-break chain, shadowing detection.
- `PathPattern` (domain model) normalisation: trailing slashes, duplicate slashes, percent-encoding, unicode segments, empty path.
- `BodySubsetMatcher`: nested objects, arrays, type strictness, explicit nulls, invalid JSON input.
- `ResolvePlaceholdersUseCase`: resolution from all three sources, missing keys, escaping, values containing quotes.
- `DecideChaosUseCase` with a fixed seed: probability boundaries (0.0 and 1.0), mode precedence, delay range bounds — fully deterministic assertions.
- Importers against golden files: OpenAPI 3.0 + 3.1 (JSON and YAML), Postman v2.1, HAR 1.2, including specs with no examples.

**Ktor `testApplication` — the wiring the unit tests can't see:**

- End-to-end request → matched response, including headers and `Content-Length`.
- Plugin ordering: pause → API key → CORS preflight → matching.
- API key accept/reject, and `OPTIONS` bypassing the guard.
- CORS preflight responses and header injection precedence.
- Chaos wiring: injected status appears, truncated body has mismatched `Content-Length`, reset closes the channel.
- Unmatched fallback with placeholder resolution.
- Hot-apply: swap the snapshot between two requests on the same connection and assert both resolve against the right ruleset.
- `413` on oversized bodies; `HEAD` returning empty body with correct length.

**Manual verification checklist** (documented in the README, not automated): screen-off endurance over an hour, hotspot mode from a laptop, Wi-Fi→hotspot transition, mDNS resolution from macOS/Linux, QR scan-to-browser, battery optimisation kill and recovery.

---

## 15. Roadmap

**v2 — hooks designed in now, not shipped:**

- **Scenario state machine** — per-route ordered response sequences (call 1 → 401, call 2 → 200) with a per-scenario reset. Design hook: `MockRoute` carries a nullable `scenarioId`, and `ResolveRouteUseCase` returns a resolution that a future `ScenarioEngine` can post-process. The resolver signature already returns a `MockResolution` rather than a bare `MockResponse` for exactly this reason.
- **WebSocket / SSE mocking** — hook: `MockRoute.protocol` field (defaulting to `HTTP`), and `ResponseWriter` behind an interface so a streaming writer can be added without touching matching.
- **JSONPath and regex matchers** — hook: `RequestMatcher` is a sealed interface; adding cases requires no changes to the scoring model beyond a point value.
- **TLS / HTTPS** with an exportable CA certificate — hook: engine config is a data class with a nullable `TlsConfig`; the connector list is built from it.

**v3 candidates:** proxy passthrough to a real backend, binary/file responses served from device storage, browser admin UI, response templating with fakers.

---

## 16. Decision Log

| Decision | Choice | Why |
|---|---|---|
| Authoring model | In-app editor + OpenAPI/Postman/HAR import | Traffic recording needs a VPN or proxy; dropped as disproportionate |
| Engine ownership | Hilt `@Singleton`, service keeps process alive | Avoids IPC and a duplicated DI graph |
| Service type | `specialUse` | `dataSync` has a cumulative daily cap on Android 15+ |
| Matching | Path + method + header/query/body-subset, most-specific wins | Expressive enough for real testing; deterministic |
| Body matchers | Subset only in v1 | Zero typing — paste a fragment; JSONPath deferred |
| Traffic storage | In-memory ring buffer, no Room | Keeps disk I/O off the request path; session-scoped data |
| Response dynamism | Path/query/header placeholders only | No fakers or scripting to debug |
| Rule edits | Hot-apply via atomic snapshot | Restart would drop live sockets |
| TLS | Deferred to v2 | Cert trust on client devices is its own project |
| Startup | Manual only | Don't open a LAN port on boot |
| Log actions | cURL, HAR, clipboard, share | Everything leaves as a standard format |
| Form factor | Phone portrait | Companion to a laptop, not a workstation |
| Distribution | Internal only | No Play review of the `specialUse` justification |

---

## 17. Open Assumptions

Flagging where the spec filled a gap the interview didn't settle:

1. **Multiple collections active simultaneously.** Since every collection carries a base-path prefix, the spec allows several enabled at once, with cross-collection conflicts resolved by the scoring chain and `collection.order`. If the intent was one active collection at a time, §4.1 and §4.3 need a filter step, not a redesign.
2. **`START_STICKY` resurrection.** "Manual start only" was specified for app launch; the spec still lets the system restart a server it killed mid-session, with a notification saying so. If unwanted, switch to `START_NOT_STICKY` and keep the "was running" detection for the recovery card only.
3. **Non-2xx OpenAPI responses** are imported as one-tap alternates in the editor rather than separate routes, since identically-shaped routes would shadow each other.
