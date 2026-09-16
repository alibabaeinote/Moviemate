package com.moviemate.app.data.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [SessionStore] a test can push values into directly — no `FirebaseUser`
 * required, since [Session] never holds one. [FirebaseSessionStore]'s own
 * derivation logic (auth state -> user doc -> pair doc) is exactly what a
 * fake here has no way to exercise; it is covered by hand-tracing the
 * `flatMapLatest` chain instead, the same way the rest of this app tests
 * pure logic rather than Firebase glue.
 */
class FakeSessionStore(initial: Session? = null) : SessionStore {
    private val _session = MutableStateFlow(initial)
    override val session: Flow<Session?> = _session

    fun emit(value: Session?) {
        _session.value = value
    }
}
