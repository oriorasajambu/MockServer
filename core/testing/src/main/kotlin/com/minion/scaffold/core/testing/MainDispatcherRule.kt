package com.minion.scaffold.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Replaces `Dispatchers.Main` with a [TestDispatcher] for the duration of a test.
 *
 * `viewModelScope` runs on `Dispatchers.Main`, which needs a real Android Looper — so without
 * this rule every ViewModel test fails at the first `launch` with "Module with the Main dispatcher
 * had failed to initialize".
 *
 * [StandardTestDispatcher] by default, not `UnconfinedTestDispatcher`: it queues coroutines rather
 * than running them eagerly, so the test controls when they execute via `advanceUntilIdle()`. The
 * unconfined variant hides ordering bugs by making everything appear synchronous.
 *
 * ```kotlin
 * class UserViewModelTest {
 *     @get:Rule val mainDispatcherRule = MainDispatcherRule()
 * }
 * ```
 */
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
