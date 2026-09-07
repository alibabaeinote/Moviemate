package com.moviemate.app.ui.screens.us

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviemate.app.di.LocalAppGraph
import com.moviemate.app.ui.components.Avatar
import com.moviemate.app.ui.components.PrimaryCta
import com.moviemate.app.ui.components.SecondaryCta
import com.moviemate.app.ui.core.ActionState
import com.moviemate.app.ui.core.factoryOf
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Change your name or picture.
 *
 * The picked image is only read into bytes here, at save time — not on pick —
 * so backing out after previewing a photo costs nothing: no upload happened,
 * only a local preview URI was ever held.
 */
@Composable
fun ProfileEditScreen(onDone: () -> Unit) {
    val graph = LocalAppGraph.current
    val viewModel: ProfileEditViewModel = viewModel(
        factory = factoryOf { ProfileEditViewModel(graph.authRepository, graph.sessionStore) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val colors = MovieMateTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedImage by remember { mutableStateOf<Uri?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) pickedImage = uri }

    LaunchedEffect(action) { if (action is ActionState.Succeeded) onDone() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceGround)
            .padding(horizontal = Space.screenGutter, vertical = Space.screenTop),
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        Text("YOUR PROFILE", style = MovieMateType.megaHeadline, color = colors.textPrimary)

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Avatar(
                name = state.name,
                avatarUrl = pickedImage?.toString() ?: state.avatarUrl,
                ringColor = colors.textAccent,
                size = 96.dp,
            )
        }

        SecondaryCta(
            label = "Choose a photo",
            onClick = {
                pickPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )

        Column(verticalArrangement = Arrangement.spacedBy(Space.inlineTight)) {
            Text("Name", style = MovieMateType.fieldLabel, color = colors.textSecondary)
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.chip),
                textStyle = MovieMateType.body,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.textAccent,
                    unfocusedBorderColor = colors.borderHairline,
                    focusedContainerColor = colors.surfaceRaised,
                    unfocusedContainerColor = colors.surfaceRaised,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.textAccent,
                ),
            )
        }

        (action as? ActionState.Failed)?.let {
            Text(it.message, style = MovieMateType.meta, color = colors.statusDecorative)
        }

        Spacer(Modifier.height(Space.stackTight))

        PrimaryCta(
            label = if (action.isRunning) "Saving…" else "Save",
            enabled = !action.isRunning && state.name.isNotBlank(),
            onClick = {
                // A phone photo can be several MB — reading it on the main
                // thread is a visible stutter, and at the top of the size
                // range, enough to trip the watchdog.
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        pickedImage?.let { uri ->
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        }
                    }
                    viewModel.save(bytes)
                }
            },
        )
        SecondaryCta(label = "Cancel", onClick = onDone)
    }
}
