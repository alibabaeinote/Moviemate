package com.moviemate.app.ui.core

/**
 * TIER 1 — the shape every screen's content takes.
 *
 * Four states, not two. "Loading or not" is the shortcut that produces a screen
 * which shows a spinner forever when a query legitimately returns nothing, and
 * a blank page when it fails. [Empty] and [Failed] are different situations for
 * the user — one is normal, one needs a retry — so they are different states.
 *
 * Screens never render this by hand; they hand it to `UiStateHost`, which is
 * what keeps every empty and error state in the app looking like the same app.
 */
sealed interface UiState<out T> {

    data object Loading : UiState<Nothing>

    /**
     * The request succeeded and there is genuinely nothing to show.
     *
     * [headline] and [detail] are the copy, not a generic "No data" — an empty
     * watchlist and an empty match are different moments in the product.
     */
    data class Empty(
        val headline: String,
        val detail: String? = null,
    ) : UiState<Nothing>

    /**
     * The request failed. [retryable] drives whether the host offers a retry
     * button: a network blip is retryable, "that invite code has expired" is not.
     */
    data class Failed(
        val message: String,
        val retryable: Boolean = true,
    ) : UiState<Nothing>

    data class Content<T>(val value: T) : UiState<T>
}

/** The content value, or null in any other state. */
val <T> UiState<T>.valueOrNull: T?
    get() = (this as? UiState.Content)?.value

inline fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    is UiState.Content -> UiState.Content(transform(value))
    is UiState.Loading -> UiState.Loading
    is UiState.Empty -> this
    is UiState.Failed -> this
}

/**
 * The state of a one-shot action the user triggered — signing in, committing to
 * a match, submitting a rating.
 *
 * Separate from [UiState] on purpose: a failed "We're in" must not blank out the
 * match card the user is looking at. Content state and action state have
 * different lifetimes and different failure handling.
 */
sealed interface ActionState {
    data object Idle : ActionState
    data object Running : ActionState
    data object Succeeded : ActionState
    data class Failed(val message: String) : ActionState

    val isRunning: Boolean get() = this is Running
}
