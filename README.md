# Android Scaffold

An empty, buildable starting point for Android apps: **Kotlin**, **Jetpack Compose**, **MVI**,
**Clean Architecture**, **Hilt**, and a Gradle multi-module graph whose boundaries are enforced by
the build rather than by code review.

There are no features. That is the point — clone it, rename the package, and run
`scripts/scaffold_feature.py` to generate the first vertical slice.

```bash
./gradlew build
```

- **UI** — Jetpack Compose, Material 3
- **Architecture** — MVI (State / Intent / Effect) over Clean Architecture layers
- **Async** — Coroutines + `StateFlow`; one-shot events over a buffered `Channel`
- **DI** — Hilt, with bindings living beside the implementations they bind
- **Network** — Retrofit + OkHttp + Gson; Chucker on debug builds only
- **Testing** — JUnit + MockK + Turbine, with a shared `MainDispatcherRule`
- **Previews** — Showkase, aggregating every `@Preview` into a browsable catalog

> `docs/` holds product specifications for a different application (Cash Flow Manager) that were
> in this repository before it became a scaffold. They are **not** scaffold documentation and
> nothing here implements them.

---

## 1. Module graph

```
:app                    Application, MainActivity, NavHost, DI aggregation — nothing else
│
├── :core:common         Pure Kotlin. AppResult, DomainError, dispatcher qualifiers,
│                        UiState/UiIntent/UiEffect markers. Depends on nothing.
├── :core:domain         Pure Kotlin. Shared models, repository interfaces, use cases.
├── :core:navigation     Pure Kotlin. @Serializable route contracts only.
├── :core:designsystem   AppTheme, colour/type/shape tokens, dumb widgets.
├── :core:ui             MviViewModel, ObserveAsEvents, DomainError → @StringRes.
├── :core:network        Shared OkHttp/Retrofit, safeCall, error mapping.
├── :core:data           Data shared between features (not a feature's own data layer).
├── :core:testing        MainDispatcherRule, fakes. Consumed via testImplementation.
│
└── :feature:*           One module per screen area. Empty — generate the first one.
```

### Dependency rules

```
:app ──► :feature:*  ──►  :core:*
:feature:a  ✗  :feature:b        never — route through :core:navigation
:core:*     ✗  :feature:*        never
:core:common depends on nothing
```

Three decisions do most of the work:

**`:core:common`, `:core:domain` and `:core:navigation` apply `mockserver.jvm.library`, not the
Android plugin.** "No Android imports in the domain layer" becomes a compile error instead of a
review comment.

**Everything in a feature module is `internal` except the navigation entry point.** A feature's
public API is `fun NavGraphBuilder.homeScreen(...)` and nothing else. Without `internal`, `:app`
can reach in and construct the ViewModel directly, and the module boundary is decorative.

**Bindings live with their implementations.** Each feature has its own `internal @Module`. A
central `:core:di` would have to depend on every feature, inverting the direction the graph exists
to enforce.

---

## 2. Convention plugins

`build-logic/` is an included build compiled before anything else configures. Module build files
apply one plugin and set a namespace; everything else is centralised, so twelve modules cannot
drift apart.

| Plugin | Applies to | Provides |
|---|---|---|
| `mockserver.android.application` | `:app` | `com.android.application`, compileSdk/minSdk/Java level, desugaring, Compose, Hilt, Showkase, test wiring |
| `mockserver.android.library` | every Android library | the same, minus Compose; plus the `check` → `compileDebugAndroidTestKotlin` guard |
| `mockserver.android.library.compose` | modules that draw | Compose BOM + bundles, Showkase processor |
| `mockserver.android.hilt` | data-layer modules | Hilt + KSP, without dragging in Compose |
| `mockserver.android.feature` | every `:feature:*` | Compose + Hilt + navigation + the core modules a feature may see |
| `mockserver.jvm.library` | pure-Kotlin modules | `kotlin-jvm` only — deliberately no Android plugin |

`check` depends on `compileDebugAndroidTestKotlin` on purpose. Neither `testDebugUnitTest` nor
`assembleDebug` builds `androidTest` sources, so they can stop compiling with a green board and
nobody finds out for weeks.

Dependency versions live in `gradle/libs.versions.toml`, shared by both builds.

---

## 3. The MVI contract

Every feature declares exactly three types.

```kotlin
// State — what the UI renders. Flat data class, safe defaults for every field.
internal data class HomeState(
    val content: ContentState = ContentState.Loading,
    val query: String = "",
) : UiState {
    sealed interface ContentState {
        data object Loading : ContentState
        data class Success(val items: List<Article>) : ContentState
        data object Empty : ContentState
        data class Failure(val error: DomainError) : ContentState
    }
}

// Intent — everything the user can do. Sealed, so `when` is exhaustive.
internal sealed interface HomeIntent : UiIntent {
    data object Load : HomeIntent
    data class Select(val id: String) : HomeIntent
}

// Effect — one-shot events that must not replay.
internal sealed interface HomeEffect : UiEffect {
    data class ShowError(val error: DomainError) : HomeEffect
    data class NavigateToDetail(val id: String) : HomeEffect
}
```

The mutually exclusive phases go in a nested `ContentState` rather than sibling booleans, because
`isLoading = true` next to a non-empty list is a state the UI would have to decide what to do
with — and this shape makes it unrepresentable.

**State or Effect?** Ask whether replaying it after a rotation would be wrong.

| | |
|---|---|
| Toast, snackbar, navigation | Effect |
| Loading flag, dialog visibility, list contents, text | State |

Storing a navigation flag in state means it fires again on every rotation. That bug is why
`Channel` exists here.

### The ViewModel

```kotlin
@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val getArticles: GetArticlesUseCase,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    override fun onIntent(intent: HomeIntent) {
        when (intent) {                       // no `else` — adding a case must not compile
            HomeIntent.Load -> load()
            is HomeIntent.Select -> viewModelScope.launch {
                emitEffect(HomeEffect.NavigateToDetail(intent.id))
            }
        }
    }

    private fun load() = viewModelScope.launch {
        getArticles()
            .onSuccess { items -> reduce { copy(content = ContentState.Success(items)) } }
            .onFailure { error ->
                reduce { copy(content = ContentState.Failure(error)) }
                emitEffect(HomeEffect.ShowError(error))
            }
    }
}
```

`MviViewModel` (in `:core:ui`) owns the `MutableStateFlow` and the `Channel`, exposes only
`state` and `effect`, and gives subclasses `reduce { }`, `currentState` and `emitEffect()`. No
screen can write state directly.

### Errors

Repositories and use cases return `AppResult<T>` — `Success(data)` or `Failure(DomainError)` —
never `kotlin.Result` and never a raw `Throwable`.

```
Exception ──► toDomainError() ──► DomainError ──► toMessageRes() ──► stringResource
   :core:network                    everywhere              :core:ui        the composable
```

- `safeCall { }` is the boundary. It rethrows `CancellationException` **before** catching
  `Throwable`, which is the line `runCatching` is missing — without it, cancelling a screen
  mid-request turns into a spurious failure and structured concurrency breaks silently.
- `DomainError` is sealed, so `when` needs no `else` and a new failure mode is a compile error at
  every site that must handle it.
- `e.message` never reaches state. It is developer text, frequently `null`, always untranslated.
- Only the composable turns an error into words, via `stringResource(error.toMessageRes())`.

### Collecting effects

Use `ObserveAsEvents` from `:core:ui`, never `LaunchedEffect(Unit) { flow.collect { } }`. The
latter is scoped to composition, and a backgrounded screen stays composed — so a `navigate()`
fires while the app is invisible and the user comes back to the wrong screen. `ObserveAsEvents`
uses `repeatOnLifecycle(STARTED)`; because effects go through a buffered `Channel`, events sent
while stopped are held and delivered on resume.

---

## 4. Adding a feature

### With the script

```bash
python scripts/scaffold_feature.py --name Home
```

Writes the whole slice — contract, ViewModel, screen with four previews, navigation entry point,
domain model, repository interface, use case, DTO, Retrofit API, mapper, repository
implementation, two Hilt modules, strings and a passing ViewModel test — then prints the three
lines you still have to add. Useful flags: `--dry-run`, `--force`, `--no-remote`, `--no-tests`,
`--plural People`.

### By hand

```
feature/home/
├── build.gradle.kts                     # id("mockserver.android.feature") + namespace. That's all.
└── src/
    ├── main/kotlin/com/rio/mockserver/feature/home/
    │   ├── presentation/                # HomeContract, HomeViewModel, HomeScreen, HomeNavigation
    │   ├── domain/                      # Article, ArticleRepository, GetArticlesUseCase
    │   ├── data/                        # ArticleApi, ArticleDto, ArticleMapper, …RepositoryImpl
    │   └── di/                          # internal @Module — @Binds beside the impl it binds
    ├── main/res/values/strings.xml
    └── test/kotlin/…/presentation/      # HomeViewModelTest
```

Layers stay **packages**, not sub-modules. Split into `:feature:home:data` / `:domain` / `:ui`
only when separate teams own the layers — otherwise packages plus `internal` give identical
enforcement with none of the Gradle overhead.

Then three edits:

1. `settings.gradle.kts` — `include(":feature:home")`
2. `app/build.gradle.kts` — `implementation(project(":feature:home"))`
3. `app/.../navigation/AppNavHost.kt` — `homeScreen(onNavigateToDetail = { … })`

Routes go in `:core:navigation`, not the feature, so another feature can navigate here without
depending on this module. Pass navigation **down as lambdas**; never hand a feature the
`NavController`, or it can reach anywhere.

Build order that works: contract first (it is the spec), then domain, data, DI, ViewModel,
screen, navigation, tests.

---

## 5. Testing

```bash
./gradlew build              # compiles everything, runs unit tests, compiles androidTest, lints
./gradlew testDebugUnitTest  # unit tests only
```

`:core:testing` supplies `MainDispatcherRule` and re-exports JUnit, MockK, Turbine and
`kotlinx-coroutines-test` as `api`, so a feature's tests get them from one dependency — which the
feature convention adds automatically.

`MainDispatcherRule` defaults to `StandardTestDispatcher`, which queues coroutines instead of
running them eagerly, so tests control execution with `advanceUntilIdle()`. `UnconfinedTestDispatcher`
would hide ordering bugs by making everything look synchronous.

Assert typed `DomainError` values, never message substrings — a substring assertion passes for the
wrong reason the moment the copy is reworded.

---

## 6. Renaming for a new project

Everything is under `com.rio.mockserver`. To rebrand:

1. Replace `com.rio.mockserver` with your package across `*.kt`, `*.kts` and
   `scripts/scaffold_feature.py` (`BASE_PACKAGE`).
2. Move the source directories to match.
3. `settings.gradle.kts` — `rootProject.name`.
4. `app/build.gradle.kts` — `applicationId`.
5. `app/src/main/res/values/strings.xml` — `app_name`; and `themes.xml` if you rename
   `Theme.MockServer`.
6. Replace the Material 3 baseline palette in `:core:designsystem` with your own tokens.

---

## 7. Things that will bite you

- **`@ShowkaseRoot` must live in `src/main`, not `src/debug`.** KSP does not scan the debug source
  set for annotations, so a root placed there compiles and silently generates nothing. Only the
  processor is debug-scoped, so nothing reaches a release build either way.
- **A `@Preview` that Showkase should catalog must be `internal`, not `private`** — Showkase
  cannot call a private function. The compose convention sets `skipPrivatePreviews=true` so a
  throwaway private preview does not fail the build; it just will not appear in the catalog.
- **A type returned from a `@Provides` is part of that module's public surface.** Hilt generates
  the component in `:app`, so the type must be on `:app`'s compile classpath — use `api`, not
  `implementation`. This is why `:core:network` exposes the networking bundle as `api`, and why
  Chucker is built inside `provideOkHttpClient` rather than provided: providing it would put a
  debug-only library on every consumer's classpath.
- **Use `LocalResources.current`, not `LocalContext.current.getString()`.** A `Context` read is not
  invalidated by a configuration change, so text goes stale after a locale switch. Lint fails the
  build on this.
- **Give every `LazyColumn` item a stable `key`.** Without one, inserting at the top shifts every
  position and recomposes every row.
- Default Gradle dependencies to `implementation`; reach for `api` only when a type appears in the
  module's own public signatures.
