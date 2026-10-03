package com.nook.msgapp.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nook.msgapp.data.Auth
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.data.friendly
import com.nook.msgapp.ui.LocalActivity
import com.nook.msgapp.ui.components.CardTone
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NCard
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NTopBar
import com.nook.msgapp.ui.components.NookLogo
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fades a block in while sliding it down a little, after [delayMs]. */
@Composable
private fun Modifier.fadeInDown(delayMs: Int): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, tween(320))
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = -(1f - p.value) * 14.dp.toPx()
    }
}

/** "Come on in.": Google sign-in, the only way into NOOK. */
@Composable
fun SignInScreen() {
    val activity = LocalActivity.current
    val c = Nook.colors
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPrivacy by remember { mutableStateOf(false) }

    fun signIn() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val ok = Auth.signInWithGoogle(activity)
                if (ok) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val message = e.friendly()
                error = message
                Toasts.show(message)
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg),
        ) {
            Box(Modifier.padding(top = Spacing.lg).fadeInDown(0)) {
                NookLogo(size = 40.dp)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
            ) {
                NText("Come on in.", NookType.display, modifier = Modifier.fadeInDown(80))
                NText(
                    "Sign in with Google, then pick the username your friends will find you by.",
                    NookType.bodyLarge,
                    c.textMuted,
                    modifier = Modifier.fadeInDown(160),
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(bottom = Spacing.lg).fadeInDown(240),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AnimatedVisibility(error != null) {
                    NText(
                        error.orEmpty(),
                        NookType.caption,
                        c.danger,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                NButton(
                    title = "Continue with Google",
                    onClick = { signIn() },
                    icon = Icons.AutoMirrored.Rounded.Login,
                    loading = busy,
                )
                val legal = buildAnnotatedString {
                    append("By continuing you agree to our ")
                    withLink(LinkAnnotation.Clickable(tag = "privacy", linkInteractionListener = { showPrivacy = true })) {
                        withStyle(SpanStyle(color = c.text, fontWeight = FontWeight.SemiBold)) { append("Privacy & Terms") }
                    }
                    append(".")
                }
                Text(
                    legal,
                    style = NookType.caption.copy(color = c.textMuted, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        AnimatedVisibility(
            visible = showPrivacy,
            enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { it / 6 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 6 },
        ) {
            PrivacySheet(onClose = { showPrivacy = false })
        }
    }
}

private val PRIVACY_SECTIONS = listOf(
    "What NOOK is" to "A private chat app for a small group of friends. It is run by the person who set it up for your group, not by a company.",
    "Not end-to-end encrypted" to "Messages, profiles and chat details are stored in Google Firebase. They are encrypted in transit and at rest by Google, and security rules only let chat members read a chat, but they are not end-to-end encrypted: whoever administers the Firebase project could technically access them.",
    "What we store" to "Your Google display name and photo, your username, the chats you are in and their messages, and a device token so we can notify you. Photos, voice notes and files are stored with Cloudinary. Your app lock never leaves your phone.",
    "Notifications" to "Notifications only say who something is from. Message text is never placed in a notification or in server logs.",
    "Disappearing messages" to "When a timer runs out, messages are hidden immediately and deleted from the server the next time a member opens the chat. Anyone could still screenshot a message before it disappears.",
    "Deleting your account" to "Account > Delete Account removes your profile, frees your username, deletes your push tokens and your sign-in.",
    "Fair use" to "Be kind. Don’t use NOOK to harass anyone or share anything illegal. The group’s admin can remove accounts that do.",
)

/** Privacy & Terms, readable before signing in (the app's navigation doesn't exist yet). */
@Composable
private fun PrivacySheet(onClose: () -> Unit) {
    val c = Nook.colors
    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        NTopBar(title = "Privacy & Terms", onBack = onClose)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            itemsIndexed(PRIVACY_SECTIONS) { i, (title, body) ->
                val highlight = i == 1
                NCard(Modifier.fillMaxWidth(), tone = if (highlight) CardTone.Highlight else CardTone.Surface) {
                    NText(title, NookType.subhead, if (highlight) c.onHighlight else c.text)
                    NText(
                        body,
                        NookType.body,
                        if (highlight) c.onHighlight else c.textMuted,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
            }
            item { Box(Modifier.navigationBarsPadding()) }
        }
    }
}
