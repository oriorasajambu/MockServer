# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

MockServer is an Android app that *is* an HTTP server: an embedded Ktor CIO engine bound to a LAN
port, with an in-app editor for the routes it serves and a live inspector for the traffic it sees.
The specification it was built from is `docs/finished/SPEC-1.md`, and KDoc throughout cites it as
`SPEC §4.1`. When changing behaviour in the request path, matching, or chaos, read the cited
section first — the comments explain *why*, and the reasons are usually load-bearing.

## Commands

```bash
./gradlew build                  # compile every module, run all 171 tests, lint, build release APK
./gradlew test                   # the suite alone (JVM modules)
./gradlew :app:installDebug      # install to a connected device
```

**`./gradlew testDebugUnitTest` runs zero tests and exits green.** Every test lives in a module
applying `mockserver.jvm.library`, which exposes `test`, not `testDebugUnitTest`; the Android
modules have no tests. It is the one command that makes an untested tree look tested. Confirm a
real run by counting `**/build/test-results/**/TEST-*.xml` — 12 suites.

Single test class or method:

```bash
./gradlew :core:domain:test --tests "*ResolveRouteUseCaseTest*"
./gradlew :core:server:test --tests "*MockServerEngineTest.HEAD returns no body*"
```

A package rename or a move leaves stale KSP output behind (a generated Showkase root under the old
path that no longer compiles). Clear `app/build/generated` by hand when that happens; `clean` is
otherwise rarely needed.

## Module graph

```
:app  →  :feature:{server,routes,traffic,settings}  →  :core:*
```

- `:core:common` — `AppResult`, `DomainError`, `safeCall`, `AppConstants`, `Logger`. Depends on nothing.
- `:core:domain` — models, repository *interfaces*, use cases. The whole matching and chaos decision surface.
- `:core:server` — the Ktor engine and its adapters. The only module that imports Ktor.
- `:core:importexport` — OpenAPI / Postman / HAR / native wire formats.
- `:core:navigation` — `@Serializable` route contracts only; how one feature reaches another.
- `:core:designsystem` — `AppTheme`, `Dimens`, colour/type tokens.
- `:core:ui` — `MviViewModel`, `ObserveAsEvents`, `DomainError → @StringRes`, and the shared widget library.
- `:core:data` — Room, DataStore, the foreground service, NSD, QR, repository *implementations*.
- `:core:testing` — `MainDispatcherRule`, consumed via `testImplementation`.

Rules the build enforces or the review expects:

- `:core:common`, `:core:domain`, `:core:server` and `:core:importexport` apply
  **`mockserver.jvm.library`, not the Android plugin**. "No Android imports below presentation" is
  therefore a compile error, and the matching pipeline, chaos engine, Ktor request path and every
  importer run on a plain JVM with no device.
- A feature never depends on another feature — route through `:core:navigation`.
- Three features depend on `:core:data`, the one documented exception, for platform mechanisms with
  no domain meaning (starting the service, encoding a QR, opening battery settings, loopback test).
- Everything in a feature is `internal` except its `NavGraphBuilder` entry point.
- **One package per screen; only `<Feature>Navigation.kt` at a feature module's root.** Each package
  holds `<Screen>Contract.kt`, `<Screen>ViewModel.kt`, `<Screen>Screen.kt`, plus any platform seam
  that screen alone needs. Packages are named for the screen, not the module — the server's main
  screen is `status/`, not `server.server`.
- Hilt bindings live with their implementations, each in its own `internal @Module`.

## How a request is served

`MockServerEngine` (in `:core:server`) does no thinking. It builds a pure `MockRequest`, calls
**`HandleMockRequestUseCase`** — the single decision point — and executes the `ResponsePlan` it gets
back: status, headers, body, how many bytes of it to write, what `Content-Length` should claim, and
whether to kill the socket afterwards.

Two things about that use case are deliberate and easy to break:

- **The transport is a lambda, not a return value.** That keeps the one piece of ordering anybody
  would get wrong — delay, then write, then log, with the duration measured around the write but
  *excluding* the delay — inside the layer a unit test can reach.
- **`decide` splits into `reject` (SPEC §4.1 steps 1-4: pause, API key, CORS preflight, the 5 MB
  ceiling) and `resolve` (steps 5-7: match, chaos, render).**

Routes live in an immutable `RoutingSnapshot` behind an `AtomicReference`. Saving compiles a new one
off the main thread and swaps it; a request reads it once at entry. **Editing a route must never
restart the engine** — only the port, bind address and API key do, and the Settings screen names
which is stale rather than restarting silently.

The traffic log must never slow the server: `offer` pushes onto a lock-free queue and pokes a
conflated channel; a coroutine on `Dispatchers.Default` drains it into a ring buffer. Anything added
to the request path must stay non-blocking and allocation-light.

## Conventions

**MVI.** Every feature declares `State`, `Intent` and `Effect` in `Contract.kt`. State or Effect?
Ask whether replaying it after a rotation would be wrong — a toast or a navigation is an Effect;
list contents, flags and text are State. `MviViewModel` owns the `MutableStateFlow` and the
`Channel` and exposes only `state` and `effect`. Collect with `ObserveAsEvents`, never
`LaunchedEffect(Unit) { flow.collect { } }` — the latter is scoped to composition, and a
backgrounded screen stays composed, so a `navigate()` fires while the app is invisible.

**ViewModels go through use cases, never repositories** (SPEC §2.2). If a use case is missing, add
one rather than injecting the repository.

**Errors.** Repositories and use cases return `AppResult<T>` — never `kotlin.Result`, never a raw
`Throwable`. `safeCall { }` is the boundary and rethrows `CancellationException` *before* catching
`Throwable`, which is the line `runCatching` is missing. The request path uses `respondCatching`
instead: a throwing handler must become a 500 on the wire plus a logged entry. `DomainError` is
sealed, so a new failure mode is a compile error at every site that must handle it; `e.message`
never reaches state.

**Resources.** All strings in `strings.xml`, all dimensions from `Dimens`, all colours from the
theme. Counted strings are `<plurals>`. Use `stringResource` where the string is known at
composition; where the resource id or its arguments only exist when an effect fires, use
`LocalResources.current`, never `LocalContext.current.getString()`.

**Tests.** Assert typed `DomainError` values, never message substrings. Importers are tested
against golden files in `core/importexport/src/test/resources/golden/`, chosen for the shapes real
exports have. Chaos tests use a fixed seed so assertions are exact rather than statistical.

## Things that have actually bitten

- **A bare `}` in a regex compiles on the JVM and throws on a device.** The JVM engine treats it as
  a literal; Android's ICU engine calls it a syntax error. Every unit test passed and the app
  crashed the moment the route editor opened. Escape both braces: `\}\}`.
- **`respondBytesWriter(contentLength = null)` selects chunked framing**, which both violates
  RFC 7230 §3.3.3 alongside a `Content-Length` and terminates the body cleanly — silently disabling
  the malformed-body chaos mode. Declare the length on an `OutgoingContent`.
- **A `Row` short of width squeezes; it does not overflow.** A `TextButton` compressed to sixty dips
  breaks its label mid-word down a four-line column, and a weighted `Text` beside three fixed
  controls shreds a path into stacked fragments. Compose warns about none of it. Give text
  `maxLines = 1` with an overflow, or use `FlowRow`. This caught three separate rows.
- **Kotlin block comments nest** — `/files/**` inside a KDoc opens a nested comment and the file
  stops compiling somewhere else entirely.
- **`@ShowkaseRoot` must live in `src/main`**; KSP does not scan `src/debug`. A `@Preview` that
  should be catalogued must be `internal`, not `private`.
- **A type returned from a `@Provides` is part of that module's public surface** — Hilt generates the
  component in `:app`, so use `api`, not `implementation`.
- **Give every `LazyColumn` item a stable `key`.** The traffic log inserts at the top constantly.
- **Never call `android.util.Log`.** Use `Logger`, whose sink is installed only in debug builds — a
  stray log line in a release build of this app is printing somebody's request bodies to logcat.
- **Unqualified KDoc links tempt the IDE into self-imports.** `[sink]` inside `Logger.kt` made
  "optimize imports" add `import ...Logger.sink` repeatedly; qualifying as `[Logger.sink]` stops it.

## Verifying on a device

The JVM suite cannot see anything platform-specific, and running the app has repeatedly found what
it missed. Worth doing for changes to the request path, the service, or discovery:

```bash
adb forward tcp:18080 tcp:8080
curl -i http://127.0.0.1:18080/some/path
```

Manual checks the tests cannot cover: screen-off endurance with a client polling, serving a laptop
over the phone's hotspot, a Wi-Fi → hotspot transition mid-session, mDNS resolution from macOS or
Linux, scanning the QR, and killing the app from recents to see the recovery card on next launch.
