package com.moviemate.app.data.session

/**
 * A plain in-memory [OnboardingDraftStore] for ViewModel tests — no
 * SharedPreferences, no Context. [flush] is inherited from the interface's
 * default implementation, so it exercises the exact same logic the real
 * store runs, not a second copy that could drift from it.
 */
class FakeOnboardingDraftStore : OnboardingDraftStore {
    private val stored = mutableListOf<OnboardingDraftStore.DraftRating>()

    override fun ratings(): List<OnboardingDraftStore.DraftRating> = stored.toList()

    override fun record(filmId: String, score: Double) {
        stored.removeAll { it.filmId == filmId }
        stored.add(OnboardingDraftStore.DraftRating(filmId, score))
    }

    override fun count(): Int = stored.size

    override fun clear() {
        stored.clear()
    }
}
