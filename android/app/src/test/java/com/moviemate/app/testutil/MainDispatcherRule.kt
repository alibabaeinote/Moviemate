package com.moviemate.app.testutil

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Redirects `Dispatchers.Main` to a test dispatcher for the duration of a
 * test, so `viewModelScope` (which launches on `Main.immediate`) has
 * somewhere to run under a plain JVM unit test — there is no real Android
 * `Looper` here.
 *
 * Unconfined, not standard: a ViewModel's `init` block starts collecting
 * `SessionStore.session` immediately, and a test drives that flow by pushing
 * values into a `MutableStateFlow` in a fake — unconfined execution means
 * the ViewModel's `state` is already updated by the time that push call
 * returns, instead of needing an explicit `advanceUntilIdle()` after every
 * fake mutation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
