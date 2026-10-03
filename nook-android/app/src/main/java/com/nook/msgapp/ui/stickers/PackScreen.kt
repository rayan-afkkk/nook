package com.nook.msgapp.ui.stickers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.nook.core.ChatLogic
import com.nook.core.Limits
import com.nook.core.Media
import com.nook.core.MessageKind
import com.nook.core.Sticker
import com.nook.msgapp.data.Chats
import com.nook.msgapp.data.Cloudinary
import com.nook.msgapp.data.Draft
import com.nook.msgapp.data.Session
import com.nook.msgapp.data.Stickers
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.ui.components.EmptyState
import com.nook.msgapp.ui.components.Loading
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NIconButton
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NTopBar
import com.nook.msgapp.ui.components.ProgressBar
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.components.rememberImagePicker
import com.nook.msgapp.ui.home.ChatPickerSheet
import com.nook.msgapp.ui.home.ConfirmDialog
import com.nook.msgapp.ui.home.Load
import com.nook.msgapp.ui.home.rememberLoad
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.launch

/** One shared sticker pack: grid of stickers, add one, send one, delete the pack (creator only). */
@Composable
fun PackScreen(packId: String, nav: NavController) {
    val c = Nook.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = Session.myUid
    val packs by rememberLoad(Unit) { Stickers.packsFlow() }
    val pack = (packs as? Load.Ready)?.value?.firstOrNull { it.id == packId }

    var progress by remember { mutableStateOf<Float?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Sticker?>(null) }

    val picker = rememberImagePicker { uri ->
        val target = pack ?: return@rememberImagePicker
        progress = 0f
        scope.launch {
            try {
                val file = Cloudinary.describe(context, uri, "sticker.jpg")
                Stickers.addSticker(context, me, target, file) { p -> progress = p }
                Toasts.show("Sticker added for everyone.")
            } catch (e: Exception) {
                Toasts.error(e)
            } finally {
                progress = null
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        NTopBar(title = pack?.name ?: "Pack", onBack = { nav.popBackStack() }) {
            if (pack != null && pack.createdBy == me) {
                NIconButton(Icons.Outlined.Delete, "Delete pack", { confirmDelete = true }, tint = c.danger)
            }
        }
        when {
            packs is Load.Loading -> Loading()
            packs is Load.Failed -> EmptyState(
                title = "Couldn't load this pack",
                body = "Check your connection.",
                icon = Icons.Outlined.CloudOff,
            )
            pack == null -> EmptyState(
                title = "Pack not found",
                body = "It may have been deleted.",
                icon = Icons.Outlined.Collections,
                action = "Go back",
                onAction = { nav.popBackStack() },
            )
            else -> {
                val full = pack.stickers.size >= Limits.MAX_STICKERS_PER_PACK
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(88.dp),
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xxl),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(bottom = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            NText(
                                "${pack.stickers.size}/${Limits.MAX_STICKERS_PER_PACK} stickers · shared with everyone on NOOK. Tap one to send it.",
                                NookType.caption,
                                c.textMuted,
                            )
                            AnimatedVisibility(progress != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                ProgressBar(progress ?: 0f)
                            }
                            NButton(
                                title = if (full) "This pack is full" else "Add a sticker",
                                onClick = { picker() },
                                icon = Icons.Outlined.PhotoLibrary,
                                loading = progress != null,
                                enabled = !full,
                            )
                        }
                    }
                    if (pack.stickers.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(title = "No stickers yet", body = "Add the first one from a photo.", icon = Icons.Outlined.Collections)
                        }
                    }
                    items(pack.stickers, key = { it.publicId.ifEmpty { it.url } }) { s ->
                        AsyncImage(
                            model = ChatLogic.thumbnailUrl(s.url, 256),
                            contentDescription = "Sticker from ${pack.name}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(c.surface)
                                .pressScale(scaleTo = 0.9f) { picked = s }
                                .padding(6.dp),
                        )
                    }
                }
            }
        }
    }

    val sticker = picked
    if (sticker != null) {
        ChatPickerSheet(
            title = "Send to",
            onDismiss = { picked = null },
            onPick = { chat, name ->
                picked = null
                scope.launch {
                    try {
                        // No publicId: deleting the message must never delete the shared pack file.
                        Chats.send(
                            chat.id,
                            chat.disappearing,
                            Session.myUid,
                            Draft(kind = MessageKind.Sticker, media = Media(url = sticker.url, width = 512, height = 512)),
                        )
                        Toasts.show("Sent to $name")
                    } catch (e: Exception) {
                        Toasts.error(e)
                    }
                }
            },
        )
    }

    if (confirmDelete && pack != null) {
        ConfirmDialog(
            title = "Delete ${pack.name}?",
            message = "The pack disappears for everyone. Stickers already sent in chats stay there.",
            confirmLabel = "Delete pack",
            destructive = true,
            loading = deleting,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                deleting = true
                scope.launch {
                    try {
                        Stickers.deletePack(pack.id)
                        confirmDelete = false
                        Toasts.show("Pack deleted.")
                        nav.popBackStack()
                    } catch (e: Exception) {
                        Toasts.error(e)
                    } finally {
                        deleting = false
                    }
                }
            },
        )
    }
}
