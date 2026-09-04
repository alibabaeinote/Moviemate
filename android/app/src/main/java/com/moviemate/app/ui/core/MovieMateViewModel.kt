package com.moviemate.app.ui.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * TIER 1 — the base every screen ViewModel extends.
 *
 * It exists for one reason: every screen in this app runs suspending work that
 * can fail, and without a shared place to put "running / failed / succeeded"
 * each screen invents its own pair of booleans. Ten screens, ten slightly
 * different bugs.
 *
 * What it deliberately does NOT do is own the content state. Content shape is
 * per-screen, so each subclass declares its own `StateFlow<UiState<Something>>`.
 */
abstract class MovieMateViewModel : ViewModel() {

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)

    /** The state of the most recent user-triggered action. */
    val action: StateFlow<ActionState> = _action.asStateFlow()

    private var inFlight: Job? = null

    /**
     * Run a user-triggered action, reporting it through [action].
     *
     * Only one action runs at a time per ViewModel: a second tap on "We're in"
     * while the first is still in flight is a double-write, not a second intent.
     * The new call is dropped rather than cancelling the first, because
     * cancelling a write that has already reached Firestore does not undo it.
     */
    protected fun runAction(
        onSuccess: suspend () -> Unit = {},
        block: suspend () -> Result<*>,
    ) {
        if (inFlight?.isActive == true) return

        inFlight = viewModelScope.launch {
            _action.value = ActionState.Running
            val result = runCatching { block() }.getOrElse { Result.failure<Unit>(it) }
            _action.value = result.fold(
                onSuccess = { ActionState.Succeeded },
                onFailure = { ActionState.Failed(it.readableMessage()) },
            )
            if (result.isSuccess) onSuccess()
        }
    }

    /**
     * Clear a failure once the user has seen it.
     *
     * Screens call this when the user edits the input that failed, so a stale
     * error is not still on screen next to a corrected form.
     */
    fun dismissActionError() {
        if (_action.value is ActionState.Failed) _action.value = ActionState.Idle
    }
}

/**
 * Turn a throwable into something worth showing a person.
 *
 * Callable errors carry copy written on the server (`joinPair` says "This
 * invite has already been used"), and that copy is better than anything a
 * generic handler could produce — so it wins. Everything else falls back to a
 * sentence that does not mention a stack trace.
 */
fun Throwable.readableMessage(): String = when (this) {
    is FirebaseFunctionsException -> message ?: "Something went wrong. Try again."
    else -> message?.takeIf { it.isNotBlank() && !it.startsWith("java.") }
        ?: "Something went wrong. Try again."
}
