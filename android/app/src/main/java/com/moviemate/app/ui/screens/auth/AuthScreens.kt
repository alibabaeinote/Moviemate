package com.moviemate.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.moviemate.app.data.repository.AuthRepository
import com.moviemate.app.ui.components.PrimaryCta
import com.moviemate.app.ui.components.SecondaryCta
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Space
import kotlinx.coroutines.launch

/** Shared page frame: warm background, one task per screen (Design System §1). */
@Composable
private fun AuthScaffold(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MovieMateTheme.colors.surfaceGround)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screenGutter, vertical = Space.screenTop),
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) { content() }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    val colors = MovieMateTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(Space.inlineTight)) {
        Text(text = label, style = MovieMateType.fieldLabel, color = colors.textSecondary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.chip),
            textStyle = MovieMateType.body,
            visualTransformation =
                if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
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
}

@Composable
private fun ErrorText(message: String?) {
    if (message == null) return
    Text(text = message, style = MovieMateType.meta, color = MovieMateTheme.colors.statusDecorative)
}

@Composable
fun WelcomeScreen(onSignUp: () -> Unit, onSignIn: () -> Unit) {
    AuthScaffold {
        Text("MOVIEMATE", style = MovieMateType.megaHeadline, color = MovieMateTheme.colors.textPrimary)
        Text(
            "One film a night, picked for both of you.",
            style = MovieMateType.body,
            color = MovieMateTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(Space.sectionGap))
        PrimaryCta(label = "Create your profile", onClick = onSignUp)
        SecondaryCta(label = "I already have an account", onClick = onSignIn)
    }
}

/**
 * Sign up.
 *
 * Order matters: the user rates films before being asked to invite anyone
 * (PRD §6) — show value before asking for commitment. So this screen leads to
 * the rating deck, not to the invite screen.
 */
@Composable
fun SignUpScreen(
    onSignedUp: () -> Unit,
    onSignInInstead: () -> Unit,
    authRepository: AuthRepository = remember { AuthRepository() },
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AuthScaffold {
        Text("CREATE YOUR PROFILE", style = MovieMateType.megaHeadline, color = MovieMateTheme.colors.textPrimary)

        Field("Name", name, { name = it })
        Field("Email", email, { email = it }, keyboardType = KeyboardType.Email)
        Field(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            keyboardType = KeyboardType.Password,
            isPassword = true,
            imeAction = ImeAction.Done,
        )

        ErrorText(error)
        Text(
            "We'll email you a verification link.",
            style = MovieMateType.meta,
            color = MovieMateTheme.colors.textSecondary,
        )

        PrimaryCta(
            label = if (busy) "Creating…" else "Continue",
            enabled = !busy && name.isNotBlank() && email.isNotBlank() && password.length >= 6,
            onClick = {
                busy = true
                error = null
                scope.launch {
                    authRepository.signUp(name, email, password)
                        .onSuccess { onSignedUp() }
                        .onFailure { error = it.message ?: "Could not create your account." }
                    busy = false
                }
            },
        )
        SecondaryCta(label = "I already have an account", onClick = onSignInInstead)
    }
}

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onForgotPassword: () -> Unit,
    onSignUpInstead: () -> Unit,
    authRepository: AuthRepository = remember { AuthRepository() },
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AuthScaffold {
        Text("WELCOME BACK", style = MovieMateType.megaHeadline, color = MovieMateTheme.colors.textPrimary)

        Field("Email", email, { email = it }, keyboardType = KeyboardType.Email)
        Field(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            keyboardType = KeyboardType.Password,
            isPassword = true,
            imeAction = ImeAction.Done,
        )

        ErrorText(error)

        PrimaryCta(
            label = if (busy) "Signing in…" else "Sign in",
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            onClick = {
                busy = true
                error = null
                scope.launch {
                    authRepository.signIn(email, password)
                        .onSuccess { onSignedIn() }
                        .onFailure { error = it.message ?: "Could not sign you in." }
                    busy = false
                }
            },
        )
        SecondaryCta(label = "Forgot password", onClick = onForgotPassword)
        SecondaryCta(label = "Create a profile instead", onClick = onSignUpInstead)
    }
}

@Composable
fun ForgotPasswordScreen(
    onDone: () -> Unit,
    authRepository: AuthRepository = remember { AuthRepository() },
) {
    var email by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AuthScaffold {
        Text("RESET PASSWORD", style = MovieMateType.megaHeadline, color = MovieMateTheme.colors.textPrimary)
        Field("Email", email, { email = it }, keyboardType = KeyboardType.Email, imeAction = ImeAction.Done)
        ErrorText(error)

        if (sent) {
            Text(
                "Check your inbox for the reset link.",
                style = MovieMateType.body,
                color = MovieMateTheme.colors.textPrimary,
            )
            PrimaryCta(label = "Back to sign in", onClick = onDone)
        } else {
            PrimaryCta(
                label = "Send reset link",
                enabled = email.isNotBlank(),
                onClick = {
                    error = null
                    scope.launch {
                        authRepository.sendPasswordReset(email)
                            .onSuccess { sent = true }
                            .onFailure { error = it.message ?: "Could not send the reset link." }
                    }
                },
            )
        }
    }
}
