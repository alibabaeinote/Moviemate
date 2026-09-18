package com.moviemate.app.ui.screens.us

import com.moviemate.app.data.model.User
import com.moviemate.app.data.repository.FakeAuthRepository
import com.moviemate.app.data.repository.UpdateProfileCall
import com.moviemate.app.data.session.FakeSessionStore
import com.moviemate.app.data.session.Session
import com.moviemate.app.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The save is deliberately one action, not two: a picture upload failure has
 * to fail the whole save rather than silently keeping the old picture while
 * reporting the name change as a success. That ordering — upload first,
 * Firestore write only on upload success — is the one thing worth pinning
 * here beyond simple wiring.
 */
class ProfileEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()

    private val alice = Session(
        uid = "alice",
        user = User(uid = "alice", name = "Ali", avatarUrl = "https://example.com/old.jpg"),
        pair = null,
    )

    @Test
    fun `loads the saved name and avatar from the session, not blank fields`() {
        val viewModel = ProfileEditViewModel(authRepository, FakeSessionStore(alice))

        assertEquals("Ali", viewModel.state.value.name)
        assertEquals("https://example.com/old.jpg", viewModel.state.value.avatarUrl)
    }

    @Test
    fun `save with no new photo keeps the existing avatar url`() {
        val viewModel = ProfileEditViewModel(authRepository, FakeSessionStore(alice))
        viewModel.setName("Ali Reza")

        viewModel.save(newAvatarBytes = null)

        assertEquals(
            listOf(UpdateProfileCall(uid = "alice", name = "Ali Reza", avatarUrl = "https://example.com/old.jpg")),
            authRepository.updatedProfiles,
        )
    }

    @Test
    fun `save with a new photo uploads first, then writes the uploaded url`() {
        authRepository.uploadAvatarResult = Result.success("https://example.com/new.jpg")
        val viewModel = ProfileEditViewModel(authRepository, FakeSessionStore(alice))

        viewModel.save(newAvatarBytes = byteArrayOf(1, 2, 3))

        assertEquals(
            listOf(UpdateProfileCall(uid = "alice", name = "Ali", avatarUrl = "https://example.com/new.jpg")),
            authRepository.updatedProfiles,
        )
    }

    @Test
    fun `an upload failure fails the whole save, never falling back to the old picture`() {
        authRepository.uploadAvatarResult = Result.failure(IllegalStateException("network down"))
        val viewModel = ProfileEditViewModel(authRepository, FakeSessionStore(alice))

        viewModel.save(newAvatarBytes = byteArrayOf(1, 2, 3))

        assertTrue(authRepository.updatedProfiles.isEmpty())
    }

    @Test
    fun `the name is trimmed before it is saved`() {
        val viewModel = ProfileEditViewModel(authRepository, FakeSessionStore(alice))
        viewModel.setName("  Ali Reza  ")

        viewModel.save(newAvatarBytes = null)

        assertEquals("Ali Reza", authRepository.updatedProfiles.single().name)
    }

    @Test
    fun `save is a no-op before the session has loaded`() {
        val viewModel = ProfileEditViewModel(authRepository, FakeSessionStore(initial = null))

        viewModel.save(newAvatarBytes = null)

        assertTrue(authRepository.updatedProfiles.isEmpty())
    }
}
