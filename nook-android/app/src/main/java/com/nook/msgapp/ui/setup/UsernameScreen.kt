package com.nook.msgapp.ui.setup

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nook.core.ChatLogic
import com.nook.core.Limits
import com.nook.core.Username
import com.nook.msgapp.data.Cloudinary
import com.nook.msgapp.data.Fb
import com.nook.msgapp.data.Profiles
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.ui.components.Avatar
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NTextField
import com.nook.msgapp.ui.components.ProgressBar
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.components.rememberImagePicker
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Availability { Idle, Checking, Available, Taken, Error }

/** Step 1 of 3: claim a permanent username, set a display name and (optionally) a photo. */
@Composable
fun UsernameScreen() {
    val c = Nook.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val user = remember { Fb.auth.currentUser }

    var displayName by remember { mutableStateOf(user?.displayName.orEmpty().take(40)) }
    var username by remember { mutableStateOf("") }
    var checkName by remember { mutableStateOf("") }
    var checkStatus by remember { mutableStateOf(Availability.Idle) }
    var saving by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var uploadProgress by remember { mutableStateOf<Float?>(null) }

    val picker = rememberImagePicker { uri -> photo = uri }

    val name = Username.normalize(username)
    val invalid = if (name.isNotEmpty()) Username.validate(name) else null
    val checkable = name.isNotEmpty() && invalid == null
    // The last answer only counts for the name it was asked about.
    val availability = when {
        !checkable -> Availability.Idle
        checkName == name -> checkStatus
        else -> Availability.Checking
    }

    // Debounced availability check: one Firestore read per pause in typing.
    LaunchedEffect(name, checkable) {
        if (checkable) {
            delay(500)
            val result = try {
                if (Profiles.isUsernameAvailable(name)) Availability.Available else Availability.Taken
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Availability.Error
            }
            checkName = name
            checkStatus = result
        }
    }

    val error = when {
        name.isEmpty() -> null
        invalid != null -> invalid
        availability == Availability.Taken -> "@$name is taken."
        availability == Availability.Error -> "Couldn't check right now."
        else -> null
    }
    val success = if (availability == Availability.Available) "@$name is yours if you want it." else null
    val canSave = checkable && availability == Availability.Available && displayName.trim().isNotEmpty() && !saving

    fun save() {
        val uid = user?.uid ?: Fb.uid ?: return
        if (!canSave) return
        saving = true
        scope.launch {
            try {
                val local = photo
                val photoURL: String? = if (local != null) {
                    uploadProgress = 0f
                    val file = Cloudinary.describe(context, local, "avatar.jpg")
                    val uploaded = Cloudinary.upload(context, file, "image") { p -> uploadProgress = p }
                    ChatLogic.thumbnailUrl(uploaded.url, 512)
                } else {
                    user?.photoUrl?.toString()
                }
                uploadProgress = null
                Profiles.claimUsername(uid, name, displayName, photoURL)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                // Session sees the new profile and moves on to the next step by itself.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Toasts.error(e)
                uploadProgress = null
                checkName = ""
                checkStatus = Availability.Idle
                saving = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
        ) {
            StepHeader(
                step = 1,
                total = 3,
                title = "Pick a username",
                subtitle = "Friends find you by your exact username. It's permanent, so pick one you'll still like next year.",
            )

            Row(
                Modifier.padding(bottom = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(Modifier.pressScale(enabled = !saving, scaleTo = 0.94f) { picker() }) {
                    Avatar(
                        name = displayName.ifBlank { "You" },
                        url = photo?.toString() ?: user?.photoUrl?.toString(),
                        size = 64.dp,
                        seed = user?.uid,
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(c.primary)
                            .border(2.dp, c.background, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = "Choose a photo", tint = c.onPrimary, modifier = Modifier.size(13.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    NText(if (photo != null) "New photo picked" else "Profile photo", NookType.bodyBold)
                    NText(
                        if (photo != null) "Tap to pick another. It uploads when you claim your username." else "Optional. Tap to pick one, or keep your Google photo.",
                        NookType.caption,
                        c.textMuted,
                    )
                }
            }

            NTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = "Display name",
                placeholder = "Your name",
                helper = "Shown on your messages. You can change it later.",
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
                maxLength = 40,
            )
            Spacer(Modifier.height(Spacing.md))
            NTextField(
                value = username,
                onValueChange = { t -> username = t.filterNot { it.isWhitespace() }.lowercase() },
                label = "Username",
                placeholder = "username",
                prefix = "@",
                error = error,
                helper = if (success != null) null else "Letters, numbers, dots and underscores.",
                imeAction = ImeAction.Done,
                onSubmit = { save() },
                maxLength = Limits.USERNAME_MAX + 1,
                trailing = {
                    when (availability) {
                        Availability.Checking -> CircularProgressIndicator(
                            color = c.textMuted,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Availability.Available -> Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Available",
                            tint = c.online,
                            modifier = Modifier.size(20.dp),
                        )
                        else -> Unit
                    }
                },
            )
            AnimatedVisibility(success != null && error == null, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
                NText(success.orEmpty(), NookType.caption, c.online, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(Spacing.lg))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val progress = uploadProgress
            AnimatedVisibility(progress != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NText("Uploading your photo…", NookType.caption, c.textMuted)
                    ProgressBar(progress ?: 0f)
                }
            }
            NButton(
                title = "Claim username",
                onClick = { save() },
                icon = Icons.Rounded.AlternateEmail,
                loading = saving,
                enabled = canSave || saving,
            )
        }
    }
}
