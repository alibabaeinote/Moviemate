package com.moviemate.app.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.moviemate.app.data.repository.AuthRepository
import com.moviemate.app.data.repository.FilmRepository
import com.moviemate.app.data.repository.PairRepository
import com.moviemate.app.data.session.OnboardingDraftStore
import com.moviemate.app.data.session.SessionStore

/**
 * The app's object graph, by hand.
 *
 * No Hilt: this app has four collaborators and one flavour of each. A DI
 * framework here would add an annotation processor and a build-time cost to
 * solve a problem that is nine lines of constructor calls. What matters is that
 * every ViewModel takes its dependencies as constructor parameters — that is
 * what makes them testable — and this is simply where the real ones are built.
 *
 * Swap the whole graph in a test or a preview by providing a different
 * implementation through [LocalAppGraph].
 */
interface AppGraph {
    val authRepository: AuthRepository
    val pairRepository: PairRepository
    val filmRepository: FilmRepository
    val sessionStore: SessionStore
    val onboardingDraftStore: OnboardingDraftStore
}

class DefaultAppGraph(context: Context) : AppGraph {
    override val authRepository = AuthRepository()
    override val pairRepository = PairRepository()
    override val filmRepository = FilmRepository()
    override val sessionStore = SessionStore(authRepository, pairRepository)
    override val onboardingDraftStore = OnboardingDraftStore(context.applicationContext)
}

/**
 * Reading this without a provider is a wiring bug, not a state to render, so it
 * fails loudly rather than handing back a half-built graph.
 */
val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error("No AppGraph provided. Wrap the UI in ProvideAppGraph.")
}
