package com.moviemate.app.ui.screens.us

import androidx.lifecycle.viewModelScope
import com.moviemate.app.data.repository.AuthRepository
import com.moviemate.app.data.session.Session
import com.moviemate.app.data.session.SessionStore
import com.moviemate.app.ui.core.MovieMateViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the form shows: the saved values until the user changes them. */
data class ProfileEditState(
    val name: String = "",
    val avatarUrl: String? = null,
)

class ProfileEditViewModel(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
) : MovieMateViewModel() {

    private val _state = MutableStateFlow(ProfileEditState())
    val state: StateFlow<ProfileEditState> = _state.asStateFlow()

    private var session: Session? = null

    init {
        viewModelScope.launch {
            val current = sessionStore.session.first { it != null }
            session = current
            _state.value = ProfileEditState(
                name = current?.displayName.orEmpty(),
                avatarUrl = current?.avatarUrl,
            )
        }
    }

    fun setName(name: String) {
        _state.value = _state.value.copy(name = name)
    }

    /**
     * Save the name, and the picture if [newAvatarBytes] holds one.
     *
     * A picked-but-not-yet-uploaded image is read to bytes by the screen (it
     * needs a Context to resolve the content:// Uri, which this ViewModel
     * deliberately does not hold) and handed in here as data.
     *
     * The upload and the Firestore write are two calls, not two independent
     * failures: if the upload fails, the save fails with it rather than
     * silently keeping the old picture and reporting success on the name
     * alone — the label on this screen says "Save", singular.
     */
    fun save(newAvatarBytes: ByteArray?) {
        val uid = session?.uid ?: return
        val name = _state.value.name.trim()
        val existingAvatarUrl = _state.value.avatarUrl

        runAction(block = block@{
            val avatarUrl = if (newAvatarBytes != null) {
                val uploaded = authRepository.uploadAvatar(uid, newAvatarBytes)
                if (uploaded.isFailure) {
                    return@block Result.failure<Unit>(
                        uploaded.exceptionOrNull() ?: IllegalStateException("Upload failed"),
                    )
                }
                uploaded.getOrThrow()
            } else {
                existingAvatarUrl
            }

            authRepository.updateProfile(uid, name, avatarUrl).onSuccess {
                _state.value = _state.value.copy(name = name, avatarUrl = avatarUrl)
            }
        })
    }
}
