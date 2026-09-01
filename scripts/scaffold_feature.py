#!/usr/bin/env python3
"""Generate a complete :feature:<name> module for this scaffold.

Writes the whole vertical slice — contract, ViewModel, screen, navigation entry point, domain,
data, DI, strings and a ViewModel test — wired to the conventions this project already enforces:

  * ViewModels extend MviViewModel from :core:ui and override onIntent()
  * State/Intent/Effect implement the markers in :core:common
  * repositories return AppResult<T> and go through safeCall from :core:network
  * routes are @Serializable classes in :core:navigation, so no feature depends on another
  * everything is `internal` except the NavGraphBuilder entry point

Adapted from the android-mvi skill's scaffold_feature.py. The parts that differ are the parts
that matter: this one generates against the base classes that exist in this repo rather than
inlining a fresh MutableStateFlow/Channel pair into every ViewModel.

Examples
--------
    python scripts/scaffold_feature.py --name Home --dry-run
    python scripts/scaffold_feature.py --name Article --plural Articles
    python scripts/scaffold_feature.py --name Settings --no-remote --no-tests

Templates use __UPPER_SNAKE__ placeholders so Kotlin's braces need no escaping.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

BASE_PACKAGE = "com.minion.scaffold"

# --------------------------------------------------------------------------------------
# Templates
# --------------------------------------------------------------------------------------

BUILD_GRADLE = """/**
 * The __NAME__ feature: contract, ViewModel, screen, and its own data and domain packages.
 *
 * Everything this module needs comes from the `minion.android.feature` convention —
 * :core:common, :core:domain, :core:navigation, :core:designsystem, :core:ui and :core:network,
 * plus Compose, Hilt and navigation. Add a dependency here only when this feature needs
 * something none of the others do.
 *
 * Its entire public surface is `NavGraphBuilder.__LOWER__Screen()`. Everything else is
 * `internal`, which is what makes the module boundary real rather than decorative.
 */
plugins {
    id("minion.android.feature")
}

android {
    namespace = "__FEATURE_PKG__"
}
"""

ROUTE = """package __PKG__

import kotlinx.serialization.Serializable

/**
 * The route to the __NAME__ screen.
 *
 * Declared here, not in the feature module, so another feature can navigate to this screen
 * without depending on it. Add constructor parameters for any arguments the screen needs — ids
 * and primitives only, never domain models.
 */
@Serializable
data object __NAME__Route : AppRoute
"""

CONTRACT = """package __PKG__

import androidx.annotation.StringRes
import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import __DOMAIN_PKG__.__NAME__

/**
 * Everything the __NAME__ screen renders.
 *
 * A flat data class with safe defaults, so the screen can draw before anything has loaded. The
 * mutually exclusive phases live in [ContentState] rather than in sibling booleans, because
 * `isLoading = true` alongside a non-empty list is a state the UI would have to decide what to
 * do with — and this shape makes it unrepresentable.
 */
internal data class __NAME__State(
    val content: ContentState = ContentState.Loading,
    val query: String = "",
    val isRefreshing: Boolean = false,
) : UiState {

    sealed interface ContentState {
        data object Loading : ContentState
        data class Success(val __PLURAL_LOWER__: List<__NAME__>) : ContentState
        data object Empty : ContentState
        data class Failure(val error: DomainError) : ContentState
    }
}

/**
 * Everything the user can do on the __NAME__ screen.
 *
 * Sealed, so the ViewModel's `when` is exhaustive and adding a case here is a compile error
 * until it is handled.
 */
internal sealed interface __NAME__Intent : UiIntent {
    data object Load : __NAME__Intent
    data object Refresh : __NAME__Intent
    data class UpdateQuery(val query: String) : __NAME__Intent
    data class Select(val id: String) : __NAME__Intent
}

/**
 * One-shot events from the __NAME__ screen.
 *
 * Errors travel as a typed [DomainError] and messages as a `@StringRes`, never as text — the
 * ViewModel does not know what language the user reads.
 */
internal sealed interface __NAME__Effect : UiEffect {
    data class ShowError(val error: DomainError) : __NAME__Effect
    data class ShowMessage(@StringRes val messageRes: Int) : __NAME__Effect
    data class NavigateToDetail(val id: String) : __NAME__Effect
}
"""

VIEWMODEL = """package __PKG__

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.common.result.onFailure
import com.minion.scaffold.core.common.result.onSuccess
import com.minion.scaffold.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import __PKG__.__NAME__State.ContentState
import __USECASE_PKG__.Get__PLURAL__UseCase
import javax.inject.Inject

/**
 * Orchestrates the __NAME__ screen. No business logic — every decision belongs in a use case.
 *
 * `internal`, like everything else in this module except the navigation entry point.
 */
@HiltViewModel
internal class __NAME__ViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val get__PLURAL__: Get__PLURAL__UseCase,
) : MviViewModel<__NAME__State, __NAME__Intent, __NAME__Effect>(
    __NAME__State(query = savedStateHandle[KEY_QUERY] ?: ""),
) {

    init {
        onIntent(__NAME__Intent.Load)
    }

    override fun onIntent(intent: __NAME__Intent) {
        when (intent) {
            __NAME__Intent.Load -> load(isRefresh = false)
            __NAME__Intent.Refresh -> load(isRefresh = true)
            is __NAME__Intent.UpdateQuery -> updateQuery(intent.query)
            is __NAME__Intent.Select -> select(intent.id)
        }
    }

    private fun load(isRefresh: Boolean) = viewModelScope.launch {
        reduce {
            if (isRefresh) copy(isRefreshing = true) else copy(content = ContentState.Loading)
        }

        get__PLURAL__()
            .onSuccess { items ->
                reduce {
                    copy(
                        content = if (items.isEmpty()) {
                            ContentState.Empty
                        } else {
                            ContentState.Success(items)
                        },
                        isRefreshing = false,
                    )
                }
            }
            .onFailure { error ->
                reduce { copy(content = ContentState.Failure(error), isRefreshing = false) }
                emitEffect(__NAME__Effect.ShowError(error))
            }
    }

    private fun updateQuery(query: String) {
        // SavedStateHandle, not just state: state lives in memory and does not survive the
        // process being killed in the background, which is the common case, not the rare one.
        savedStateHandle[KEY_QUERY] = query
        reduce { copy(query = query) }
        // TODO: debounce and re-filter, or feed the query into the upstream use case.
    }

    private fun select(id: String) = viewModelScope.launch {
        emitEffect(__NAME__Effect.NavigateToDetail(id))
    }

    private companion object {
        const val KEY_QUERY = "__LOWER___query"
    }
}
"""

SCREEN = """package __PKG__

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.designsystem.theme.AppSpacing
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.ui.error.toMessageRes
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import kotlinx.coroutines.launch
import __FEATURE_PKG__.R
import __PKG__.__NAME__State.ContentState
import __DOMAIN_PKG__.__NAME__

/**
 * The connected screen: owns the ViewModel and collects one-shot effects.
 *
 * Split from [__NAME__Content] so the rendering half can be previewed and tested with no Hilt,
 * no ViewModel and no coroutines.
 *
 * Takes navigation as a lambda rather than a NavController — a feature that holds the controller
 * can navigate anywhere, which is the same as knowing about every other feature.
 */
@Composable
internal fun __NAME__Screen(
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: __NAME__ViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // LocalResources, not LocalContext.current.getString: a Context read is not invalidated when
    // the Configuration changes, so after a locale switch the snackbar would show stale text.
    val resources = LocalResources.current

    // Lifecycle-aware, not LaunchedEffect(Unit) — see ObserveAsEvents for why that matters.
    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is __NAME__Effect.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(effect.error.toMessageRes()))
            }
            is __NAME__Effect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(effect.messageRes))
            }
            is __NAME__Effect.NavigateToDetail -> onNavigateToDetail(effect.id)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        __NAME__Content(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * The stateless half. Takes the whole state because it is the distribution point; the composables
 * below it take only the fields they read, so a change to one field does not recompose the rest.
 */
@Composable
internal fun __NAME__Content(
    state: __NAME__State,
    onIntent: (__NAME__Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val content = state.content) {
            ContentState.Loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )

            ContentState.Empty -> Text(
                text = stringResource(R.string.__LOWER___empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center),
            )

            is ContentState.Failure -> Text(
                // The UI is the only layer that turns an error into text.
                text = stringResource(content.error.toMessageRes()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center),
            )

            is ContentState.Success -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                // A stable key: without it, inserting at the top shifts every position and
                // recomposes every row.
                items(content.__PLURAL_LOWER__, key = { it.id }) { item ->
                    __NAME__Row(
                        name = item.name,
                        onClick = { onIntent(__NAME__Intent.Select(item.id)) },
                    )
                }
            }
        }
    }
}

/** One row. Takes the fields it reads, not the whole model — cheaper to recompose, easy to preview. */
@Composable
private fun __NAME__Row(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = name,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.padding(AppSpacing.medium),
    )
}

// Four previews, one per state. The failure and empty cases are the ones that ship broken,
// because they are the ones nobody looks at while building the happy path.

@Preview(showBackground = true)
@Composable
internal fun __NAME__ContentLoadingPreview() {
    AppTheme {
        __NAME__Content(state = __NAME__State(content = ContentState.Loading), onIntent = {})
    }
}

@Preview(showBackground = true)
@Composable
internal fun __NAME__ContentEmptyPreview() {
    AppTheme {
        __NAME__Content(state = __NAME__State(content = ContentState.Empty), onIntent = {})
    }
}

@Preview(showBackground = true)
@Composable
internal fun __NAME__ContentFailurePreview() {
    AppTheme {
        __NAME__Content(
            state = __NAME__State(content = ContentState.Failure(DomainError.NoInternet)),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun __NAME__ContentSuccessPreview() {
    AppTheme {
        __NAME__Content(
            state = __NAME__State(
                content = ContentState.Success(
                    listOf(
                        __NAME__(id = "1", name = "First"),
                        __NAME__(id = "2", name = "Second"),
                    ),
                ),
            ),
            onIntent = {},
        )
    }
}
"""

NAVIGATION = """package __PKG__

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.__NAME__Route

/**
 * This module's entire public surface.
 *
 * `:app` calls this from its NavHost; nothing else in the module is visible to anyone. That is
 * what `internal` on the contract, the ViewModel and the screen buys — the boundary is enforced
 * by the compiler rather than by convention.
 */
fun NavGraphBuilder.__LOWER__Screen(
    onNavigateToDetail: (String) -> Unit,
) {
    composable<__NAME__Route> {
        __NAME__Screen(onNavigateToDetail = onNavigateToDetail)
    }
}
"""

MODEL = """package __PKG__

/**
 * The __NAME__ domain model.
 *
 * Pure Kotlin: no annotations, no framework types, nothing that ties it to how it arrived. It
 * lives in this feature because only this feature uses it — promote it to :core:domain the day a
 * second feature needs it, not before.
 */
internal data class __NAME__(
    val id: String,
    val name: String,
    // TODO: replace with the real fields, then update __NAME__Mapper.
)
"""

REPOSITORY = """package __PKG__

import com.minion.scaffold.core.common.result.AppResult

/**
 * What the __NAME__ feature needs from the outside world, stated in the domain's own terms.
 *
 * Declared in `domain`, implemented in `data`. That inversion is what lets the use case be tested
 * with no network and no Android.
 */
internal interface __NAME__Repository {

    suspend fun get__PLURAL__(): AppResult<List<__NAME__>>

    suspend fun get__NAME__ById(id: String): AppResult<__NAME__>
}
"""

USECASE = """package __PKG__

import com.minion.scaffold.core.common.result.AppResult
import javax.inject.Inject

/**
 * Reads the __PLURAL_LOWER__.
 *
 * One class, one action, `operator fun invoke` — so the call site reads `get__PLURAL__()`. A
 * "__NAME__UseCase" with six methods would be a service class wearing a use case's name.
 *
 * Trivial today because it only delegates. It still earns its place: when sorting, filtering or
 * a second source arrives, this is where it goes, and the ViewModel does not change.
 */
internal class Get__PLURAL__UseCase @Inject constructor(
    private val repository: __NAME__Repository,
) {
    suspend operator fun invoke(): AppResult<List<__NAME__>> = repository.get__PLURAL__()
}
"""

DTO = """package __PKG__

import com.google.gson.annotations.SerializedName

/**
 * The wire shape of a __NAME__. Mirrors the API exactly, including any names the domain would
 * never choose.
 *
 * `internal`, and it must stay that way — a DTO crossing a module boundary makes the wire format
 * part of the app's shared vocabulary, and then the API cannot change without touching screens.
 *
 * Every field is nullable because the server's contract is a promise, not a guarantee.
 * [toDomain] is where that uncertainty is resolved, once.
 */
internal data class __NAME__Dto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
)
"""

API = """package __PKG__

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * The __NAME__ endpoints.
 *
 * Built from the shared `Retrofit` in :core:network by this feature's Hilt module, and kept
 * `internal` — no other module has any business calling it.
 */
internal interface __NAME__Api {

    @GET("__PLURAL_LOWER__")
    suspend fun get__PLURAL__(): List<__NAME__Dto>

    @GET("__PLURAL_LOWER__/{id}")
    suspend fun get__NAME__ById(@Path("id") id: String): __NAME__Dto
}
"""

MAPPER = """package __PKG__

import __DOMAIN_PKG__.__NAME__

/**
 * DTO to domain, in one place.
 *
 * This is where the wire's nullability becomes the domain's certainty. Defaulting is a decision:
 * an absent name rendering as "" is a choice to show an empty row rather than to fail. Where that
 * is wrong, return a failure from the repository instead.
 */
internal fun __NAME__Dto.toDomain(): __NAME__ = __NAME__(
    id = id.orEmpty(),
    name = name.orEmpty(),
)
"""

REPOSITORY_IMPL = """package __PKG__

import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.common.result.map
import com.minion.scaffold.core.network.error.safeCall
import __DOMAIN_PKG__.__NAME__
import __DOMAIN_PKG__.__NAME__Repository
import javax.inject.Inject

/**
 * Satisfies [__NAME__Repository] from the network.
 *
 * `safeCall` is the error boundary: exceptions exist below this line and nowhere above it. Note
 * it rethrows `CancellationException` rather than converting it into a failure, which is the bug
 * `runCatching` would introduce here.
 */
internal class __NAME__RepositoryImpl @Inject constructor(
    private val api: __NAME__Api,
) : __NAME__Repository {

    override suspend fun get__PLURAL__(): AppResult<List<__NAME__>> = safeCall {
        api.get__PLURAL__().map { it.toDomain() }
    }

    override suspend fun get__NAME__ById(id: String): AppResult<__NAME__> = safeCall {
        api.get__NAME__ById(id).toDomain()
    }
}
"""

REPOSITORY_IMPL_STUB = """package __PKG__

import com.minion.scaffold.core.common.result.AppResult
import __DOMAIN_PKG__.__NAME__
import __DOMAIN_PKG__.__NAME__Repository
import javax.inject.Inject

/**
 * Satisfies [__NAME__Repository].
 *
 * Generated with `--no-remote`, so there is no data source yet. Wire one up and return real
 * results — every method below is a placeholder.
 */
internal class __NAME__RepositoryImpl @Inject constructor() : __NAME__Repository {

    override suspend fun get__PLURAL__(): AppResult<List<__NAME__>> =
        AppResult.Success(emptyList()) // TODO: read from a real source.

    override suspend fun get__NAME__ById(id: String): AppResult<__NAME__> =
        AppResult.Success(__NAME__(id = id, name = "")) // TODO: read from a real source.
}
"""

DI_MODULE = """package __PKG__

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import __DATA_PKG__.__NAME__RepositoryImpl
import __DOMAIN_PKG__.__NAME__Repository
import javax.inject.Singleton

/**
 * Binds this feature's implementations, inside this feature.
 *
 * Deliberately not a central DI module. One would have to depend on every feature to bind them,
 * which inverts the direction the module graph exists to enforce. `internal` because Hilt's
 * generated code lives in the same module and does not need it to be public.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class __NAME__Module {

    @Binds
    @Singleton
    abstract fun bind__NAME__Repository(impl: __NAME__RepositoryImpl): __NAME__Repository
}
"""

DI_NETWORK = """package __PKG__

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create
import __DATA_PKG__.__NAME__Api
import javax.inject.Singleton

/**
 * Creates this feature's API from the shared `Retrofit` in :core:network.
 *
 * The client, the converter and the interceptors are configured once, centrally; what belongs to
 * a feature is only its own endpoints.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object __NAME__NetworkModule {

    @Provides
    @Singleton
    fun provide__NAME__Api(retrofit: Retrofit): __NAME__Api = retrofit.create()
}
"""

STRINGS = """<?xml version="1.0" encoding="utf-8"?>
<!--
  Copy for the __NAME__ screen, in the module that shows it.

  Strings live beside the screen that uses them rather than in a shared file, so deleting the
  feature deletes its copy too.
-->
<resources>
    <string name="__LOWER___empty">Nothing here yet.</string>
    <string name="__LOWER___retry">Try again</string>
</resources>
"""

TEST = """package __PKG__

import app.cash.turbine.test
import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.testing.MainDispatcherRule
import __DOMAIN_PKG__.__NAME__
import __USECASE_PKG__.Get__PLURAL__UseCase
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tests the contract, not the implementation: every intent leads to the state or the effect it
 * promises.
 *
 * Errors are asserted as typed [DomainError] values, never as message substrings — a substring
 * assertion passes for the wrong reason the moment the copy is reworded.
 */
internal class __NAME__ViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val get__PLURAL__: Get__PLURAL__UseCase = mockk()

    private fun viewModel() = __NAME__ViewModel(
        savedStateHandle = SavedStateHandle(),
        get__PLURAL__ = get__PLURAL__,
    )

    @Test
    fun `load puts the __PLURAL_LOWER__ into state`() = runTest {
        val items = listOf(__NAME__(id = "1", name = "First"))
        coEvery { get__PLURAL__() } returns AppResult.Success(items)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(__NAME__State.ContentState.Success(items), viewModel.state.value.content)
    }

    @Test
    fun `load with no results reports empty, not success with an empty list`() = runTest {
        coEvery { get__PLURAL__() } returns AppResult.Success(emptyList())

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(__NAME__State.ContentState.Empty, viewModel.state.value.content)
    }

    @Test
    fun `a failed load lands in state and raises an effect`() = runTest {
        coEvery { get__PLURAL__() } returns AppResult.Failure(DomainError.NoInternet)

        val viewModel = viewModel()

        viewModel.effect.test {
            advanceUntilIdle()
            assertEquals(__NAME__Effect.ShowError(DomainError.NoInternet), awaitItem())
        }
        assertEquals(
            __NAME__State.ContentState.Failure(DomainError.NoInternet),
            viewModel.state.value.content,
        )
    }

    @Test
    fun `selecting an item navigates`() = runTest {
        coEvery { get__PLURAL__() } returns AppResult.Success(emptyList())

        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onIntent(__NAME__Intent.Select("42"))
            advanceUntilIdle()
            assertEquals(__NAME__Effect.NavigateToDetail("42"), awaitItem())
        }
    }
}
"""


# --------------------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------------------

def pluralize(name: str) -> str:
    """Naive English pluralisation; override with --plural when it guesses wrong."""
    if re.search(r"(s|x|z|ch|sh)$", name):
        return name + "es"
    if re.search(r"[^aeiou]y$", name):
        return name[:-1] + "ies"
    return name + "s"


def strip_same_package_imports(text: str) -> str:
    """Drop `import a.b.C` lines whose package equals the file's own package."""
    match = re.match(r"package ([\w.]+)", text)
    if not match:
        return text
    own = match.group(1)
    kept = [
        line for line in text.splitlines()
        if not (line.startswith("import ") and line[len("import "):].rsplit(".", 1)[0] == own)
    ]
    return "\n".join(kept) + "\n"


def sort_imports(text: str) -> str:
    """Put the import block in the order the Kotlin style guide (and the IDE) expects."""
    lines = text.splitlines()
    first = next((i for i, l in enumerate(lines) if l.startswith("import ")), None)
    if first is None:
        return text
    last = max(i for i, l in enumerate(lines) if l.startswith("import "))
    block = sorted({l for l in lines[first:last + 1] if l.startswith("import ")})
    # javax.* and java.* sort after everything else, as IntelliJ arranges them.
    java = [l for l in block if l.startswith(("import java.", "import javax."))]
    rest = [l for l in block if l not in java]
    return "\n".join(lines[:first] + rest + java + lines[last + 1:]) + "\n"


def collapse_blank_runs(text: str) -> str:
    return re.sub(r"\n{3,}", "\n\n", text)


def packages(name: str) -> dict[str, str]:
    lower = name[0].lower() + name[1:]
    feature = f"{BASE_PACKAGE}.feature.{lower}"
    return {
        "feature": feature,
        "presentation": f"{feature}.presentation",
        "domain": f"{feature}.domain",
        "usecase": f"{feature}.domain",
        "data": f"{feature}.data",
        "di": f"{feature}.di",
        "navigation": f"{BASE_PACKAGE}.core.navigation",
    }


def plan(args) -> dict[str, str]:
    """Return {path relative to the repo root: rendered content}."""
    name = args.name
    plural = args.plural or pluralize(name)
    lower = name[0].lower() + name[1:]
    pkgs = packages(name)

    subs = {
        "__NAME__": name,
        "__PLURAL__": plural,
        "__PLURAL_LOWER__": plural[0].lower() + plural[1:],
        "__LOWER__": lower,
        "__FEATURE_PKG__": pkgs["feature"],
        "__PRES_PKG__": pkgs["presentation"],
        "__DOMAIN_PKG__": pkgs["domain"],
        "__USECASE_PKG__": pkgs["usecase"],
        "__DATA_PKG__": pkgs["data"],
        "__DI_PKG__": pkgs["di"],
    }

    def render(template: str, own_pkg: str) -> str:
        out = template.replace("__PKG__", own_pkg)
        for key, value in subs.items():
            out = out.replace(key, value)
        return collapse_blank_runs(sort_imports(strip_same_package_imports(out)))

    module = f"feature/{lower}"
    main = f"{module}/src/main/kotlin"
    test = f"{module}/src/test/kotlin"

    def path(root: str, pkg: str, filename: str) -> str:
        return f"{root}/{pkg.replace('.', '/')}/{filename}"

    files: dict[str, str] = {
        f"{module}/build.gradle.kts": render(BUILD_GRADLE, pkgs["feature"]),
        f"{module}/src/main/res/values/strings.xml": render(STRINGS, pkgs["feature"]),

        # The route goes into :core:navigation, not the feature — that is what lets another
        # feature navigate here without depending on this module.
        path("core/navigation/src/main/kotlin", pkgs["navigation"], f"{name}Route.kt"):
            render(ROUTE, pkgs["navigation"]),

        path(main, pkgs["presentation"], f"{name}Contract.kt"):
            render(CONTRACT, pkgs["presentation"]),
        path(main, pkgs["presentation"], f"{name}ViewModel.kt"):
            render(VIEWMODEL, pkgs["presentation"]),
        path(main, pkgs["presentation"], f"{name}Screen.kt"):
            render(SCREEN, pkgs["presentation"]),
        path(main, pkgs["presentation"], f"{name}Navigation.kt"):
            render(NAVIGATION, pkgs["presentation"]),

        path(main, pkgs["domain"], f"{name}.kt"): render(MODEL, pkgs["domain"]),
        path(main, pkgs["domain"], f"{name}Repository.kt"): render(REPOSITORY, pkgs["domain"]),
        path(main, pkgs["usecase"], f"Get{plural}UseCase.kt"): render(USECASE, pkgs["usecase"]),

        path(main, pkgs["di"], f"{name}Module.kt"): render(DI_MODULE, pkgs["di"]),
    }

    if args.remote:
        files[path(main, pkgs["data"], f"{name}Dto.kt")] = render(DTO, pkgs["data"])
        files[path(main, pkgs["data"], f"{name}Api.kt")] = render(API, pkgs["data"])
        files[path(main, pkgs["data"], f"{name}Mapper.kt")] = render(MAPPER, pkgs["data"])
        files[path(main, pkgs["data"], f"{name}RepositoryImpl.kt")] = \
            render(REPOSITORY_IMPL, pkgs["data"])
        files[path(main, pkgs["di"], f"{name}NetworkModule.kt")] = \
            render(DI_NETWORK, pkgs["di"])
    else:
        files[path(main, pkgs["data"], f"{name}RepositoryImpl.kt")] = \
            render(REPOSITORY_IMPL_STUB, pkgs["data"])

    if args.tests:
        files[path(test, pkgs["presentation"], f"{name}ViewModelTest.kt")] = \
            render(TEST, pkgs["presentation"])

    return files


def next_steps(name: str, remote: bool) -> list[str]:
    lower = name[0].lower() + name[1:]
    model = (
        f"Replace the placeholder fields on {name} and {name}Dto, then update {name}Mapper."
        if remote
        else f"Replace the placeholder fields on {name}, then give {name}RepositoryImpl a real source."
    )
    return [
        f'Add `include(":feature:{lower}")` to settings.gradle.kts.',
        f'Add `implementation(project(":feature:{lower}"))` to app/build.gradle.kts.',
        f"Register the screen in AppNavHost: `{lower}Screen(onNavigateToDetail = {{ /* ... */ }})`.",
        model,
        "Resolve every TODO, then run ./gradlew build.",
    ]


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Scaffold a :feature:<name> module for this project.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--name", required=True,
                        help="Feature name in PascalCase, singular (e.g. Home, Article)")
    parser.add_argument("--plural", help="Override the guessed plural (e.g. People for Person)")
    parser.add_argument("--output-dir", default=".",
                        help="Repo root to write into (default: current directory)")
    parser.add_argument("--no-remote", dest="remote", action="store_false",
                        help="Skip the DTO, Retrofit API, mapper and network module")
    parser.add_argument("--no-tests", dest="tests", action="store_false",
                        help="Skip the ViewModel test")
    parser.add_argument("--dry-run", action="store_true", help="Print the plan without writing")
    parser.add_argument("--force", action="store_true", help="Overwrite existing files")
    parser.set_defaults(remote=True, tests=True)

    args = parser.parse_args()

    if not re.fullmatch(r"[A-Z][A-Za-z0-9]*", args.name):
        parser.error("--name must be PascalCase and start with a capital, e.g. Home")

    files = plan(args)
    root = Path(args.output_dir)

    existing = [p for p in files if (root / p).exists()]
    if existing and not args.force and not args.dry_run:
        print("Refusing to overwrite existing files (pass --force):", file=sys.stderr)
        for p in existing:
            print(f"  {p}", file=sys.stderr)
        return 1

    for rel, content in sorted(files.items()):
        target = root / rel
        if args.dry_run:
            print(f"[dry-run] {rel}  ({len(content.splitlines())} lines)")
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
        print(f"created {rel}")

    print(f"\n{len(files)} files {'planned' if args.dry_run else 'written'}. Next:")
    for i, step in enumerate(next_steps(args.name, args.remote), 1):
        print(f"  {i}. {step}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
