package com.nook.msgapp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.nook.core.ChatLogic
import com.nook.core.Limits
import com.nook.core.Media
import com.nook.core.MessageKind
import com.nook.core.StickerPack
import com.nook.msgapp.data.AppConfig
import com.nook.msgapp.data.Chats
import com.nook.msgapp.data.Cloudinary
import com.nook.msgapp.data.Draft
import com.nook.msgapp.data.Giphy
import com.nook.msgapp.data.GiphyItem
import com.nook.msgapp.data.GiphyType
import com.nook.msgapp.data.Session
import com.nook.msgapp.data.Stickers
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.data.friendly
import com.nook.msgapp.nav.Routes
import com.nook.msgapp.ui.components.ButtonVariant
import com.nook.msgapp.ui.components.CardTone
import com.nook.msgapp.ui.components.EmptyState
import com.nook.msgapp.ui.components.Loading
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NCard
import com.nook.msgapp.ui.components.NHeader
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NTextField
import com.nook.msgapp.ui.components.ProgressBar
import com.nook.msgapp.ui.components.SectionLabel
import com.nook.msgapp.ui.components.Segmented
import com.nook.msgapp.ui.components.Skeleton
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.components.rememberImagePicker
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Tag(val label: String, val tone: Int, val tilt: Float)

/** Pastel index: 0 mint, 1 sky, 2 peach, 3 lavender, 4 rose. */
private val TAGS = listOf(
    Tag("lol", 2, -6f),
    Tag("mood", 3, 4f),
    Tag("hype", 0, -3f),
    Tag("sorry", 4, 7f),
    Tag("birthday", 1, -5f),
    Tag("no way", 2, 3f),
    Tag("sleepy", 0, -7f),
    Tag("yes!!", 3, 5f),
)

@Composable
fun StickersTab(nav: NavController) {
    val c = Nook.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = Session.myUid
    val packs by rememberLoad(Unit) { Stickers.packsFlow() }

    // New pack
    var packName by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    val picker = rememberImagePicker { uri ->
        val name = packName
        progress = 0f
        scope.launch {
            try {
                val file = Cloudinary.describe(context, uri, "sticker.jpg")
                Stickers.createPack(context, me, name, file) { p -> progress = p }
                packName = ""
                Toasts.show("Pack created for everyone.")
            } catch (e: Exception) {
                Toasts.error(e)
            } finally {
                progress = null
            }
        }
    }

    // Giphy
    var type by rememberSaveable { mutableStateOf(GiphyType.Gifs) }
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember { mutableStateListOf<GiphyItem>() }
    var next by remember { mutableStateOf<Int?>(null) }
    var gifLoading by remember { mutableStateOf(false) }
    var gifError by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<GiphyItem?>(null) }

    LaunchedEffect(type, query) {
        if (!AppConfig.giphyConfigured) return@LaunchedEffect
        delay(if (query.isBlank()) 0 else 450)
        gifLoading = true
        gifError = null
        try {
            val (items, n) = Giphy.search(type, query, 0)
            results.clear()
            results.addAll(items)
            next = n
        } catch (e: Exception) {
            gifError = e.friendly()
            results.clear()
            next = null
        } finally {
            gifLoading = false
        }
    }

    fun loadMore() {
        val offset = next ?: return
        if (gifLoading) return
        gifLoading = true
        scope.launch {
            try {
                val (items, n) = Giphy.search(type, query, offset)
                val known = results.map { it.id }.toSet()
                results.addAll(items.filter { it.id !in known })
                next = n
            } catch (e: Exception) {
                Toasts.error(e)
            } finally {
                gifLoading = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        NHeader("Stickers")
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (type == GiphyType.Gifs) 2 else 3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xxl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            full("title") {
                NText("Find the perfect reaction", NookType.display, modifier = Modifier.padding(bottom = Spacing.xs))
            }

            /* ---------------- Our packs ---------------- */
            full("packsLabel") { SectionLabel("Our packs") }
            full("create") {
                NCard(Modifier.fillMaxWidth(), tone = CardTone.Surface) {
                    NText("Make a sticker pack", NookType.subhead)
                    NText(
                        "Name it, pick a photo, and it becomes the first sticker. Packs are shared with everyone on NOOK.",
                        NookType.caption,
                        c.textMuted,
                        modifier = Modifier.padding(top = 2.dp, bottom = Spacing.sm),
                    )
                    NTextField(
                        value = packName,
                        onValueChange = { packName = it },
                        label = "Pack name",
                        placeholder = "Inside jokes",
                        maxLength = 30,
                    )
                    AnimatedVisibility(progress != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        ProgressBar(progress ?: 0f, Modifier.padding(top = Spacing.sm))
                    }
                    NButton(
                        title = "Choose a photo",
                        onClick = { picker() },
                        modifier = Modifier.padding(top = Spacing.sm),
                        icon = Icons.Outlined.PhotoLibrary,
                        loading = progress != null,
                        enabled = packName.isNotBlank(),
                    )
                }
            }
            when (val p = packs) {
                Load.Loading -> full("packsLoading") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        repeat(2) { Skeleton(Modifier.fillMaxWidth().heightIn(min = 88.dp)) }
                    }
                }
                is Load.Failed -> full("packsError") {
                    EmptyState(title = "Couldn't load packs", body = "Check your connection.", icon = Icons.Outlined.CloudOff)
                }
                is Load.Ready -> {
                    if (p.value.isEmpty()) {
                        full("packsEmpty") {
                            EmptyState(
                                title = "No packs yet",
                                body = "Turn any photo into a sticker and start the first pack for your crew.",
                                icon = Icons.Outlined.Collections,
                            )
                        }
                    } else {
                        p.value.forEach { pack ->
                            full("pack:${pack.id}") { PackCard(pack) { nav.navigate(Routes.pack(pack.id)) } }
                        }
                    }
                }
            }

            /* ---------------- Giphy ---------------- */
            full("gifLabel") { SectionLabel("GIFs & stickers") }
            if (!AppConfig.giphyConfigured) {
                full("gifOff") {
                    NCard(Modifier.fillMaxWidth(), tone = CardTone.Highlight) {
                        NText("GIF search is off", NookType.subhead, c.onHighlight)
                        NText(
                            "This build has no Giphy key. Add nook.giphyApiKey to gradle.properties and rebuild to search GIFs and stickers.",
                            NookType.body,
                            c.onHighlight.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                    }
                }
            } else {
                full("gifControls") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Segmented(
                            options = listOf(GiphyType.Gifs to "GIFs", GiphyType.Stickers to "Stickers"),
                            selected = type,
                            onSelect = { type = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        NTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = "Search",
                            placeholder = "GIFs and stickers",
                            imeAction = ImeAction.Search,
                            maxLength = 50,
                            trailing = { Icon(Icons.Rounded.Search, null, tint = c.textMuted, modifier = Modifier.size(20.dp)) },
                        )
                        TagCloud(onPick = { query = it })
                    }
                }
                if (gifError != null && results.isEmpty()) {
                    full("gifError") { EmptyState(title = "Couldn't reach Giphy", body = gifError ?: "", icon = Icons.Outlined.CloudOff) }
                } else if (gifLoading && results.isEmpty()) {
                    full("gifLoading") { Box(Modifier.fillMaxWidth().heightIn(min = 120.dp)) { Loading() } }
                } else if (results.isEmpty()) {
                    full("gifEmpty") { EmptyState(title = "Nothing found", body = "Try another word.", icon = Icons.Rounded.Search) }
                } else {
                    items(results, key = { "g:${it.id}" }) { item ->
                        AsyncImage(
                            model = item.url,
                            contentDescription = item.title.ifEmpty { "GIF" },
                            contentScale = if (type == GiphyType.Gifs) ContentScale.Crop else ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(if (type == GiphyType.Gifs) c.surfaceRaised else c.surface)
                                .pressScale(scaleTo = 0.94f) { picked = item },
                        )
                    }
                    if (next != null) {
                        full("more") {
                            NButton(
                                title = "Load more",
                                onClick = { loadMore() },
                                variant = ButtonVariant.Secondary,
                                loading = gifLoading,
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                    }
                    full("giphyCredit") {
                        NText(
                            "Powered by GIPHY",
                            NookType.micro,
                            c.textMuted,
                            modifier = Modifier.padding(top = Spacing.sm),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    val item = picked
    if (item != null) {
        val sendType = type
        ChatPickerSheet(
            title = "Send to",
            onDismiss = { picked = null },
            onPick = { chat, name ->
                picked = null
                scope.launch {
                    try {
                        Chats.send(
                            chat.id,
                            chat.disappearing,
                            Session.myUid,
                            Draft(
                                kind = if (sendType == GiphyType.Gifs) MessageKind.Gif else MessageKind.Sticker,
                                media = Media(url = item.url, previewUrl = item.previewUrl, width = item.width, height = item.height),
                            ),
                        )
                        Toasts.show("Sent to $name")
                    } catch (e: Exception) {
                        Toasts.error(e)
                    }
                }
            },
        )
    }
}

/** A full-width grid row. */
private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun PackCard(pack: StickerPack, onClick: () -> Unit) {
    val c = Nook.colors
    NCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                NText(pack.name, NookType.subhead, maxLines = 1)
                NText(
                    "${pack.stickers.size}/${Limits.MAX_STICKERS_PER_PACK} ${if (pack.stickers.size == 1) "sticker" else "stickers"}",
                    NookType.caption,
                    c.textMuted,
                )
            }
        }
        if (pack.stickers.isNotEmpty()) {
            Row(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                pack.stickers.take(5).forEach { s ->
                    AsyncImage(
                        model = ChatLogic.thumbnailUrl(s.url, 160),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(52.dp),
                    )
                }
                if (pack.stickers.size > 5) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(Radius.sm)).background(c.surfaceRaised),
                        contentAlignment = Alignment.Center,
                    ) {
                        NText("+${pack.stickers.size - 5}", NookType.captionBold, c.textMuted)
                    }
                }
            }
        }
    }
}

/** Trending words as tilted pastel chips that float gently. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagCloud(onPick: (String) -> Unit) {
    val c = Nook.colors
    val transition = rememberInfiniteTransition(label = "tags")
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        TAGS.forEachIndexed { i, t ->
            val dy by transition.animateFloat(
                initialValue = -3f,
                targetValue = 3f,
                animationSpec = infiniteRepeatable(tween(2400 + i * 120), RepeatMode.Reverse),
                label = "tag$i",
            )
            Box(
                Modifier
                    .graphicsLayer {
                        rotationZ = t.tilt
                        translationY = dy * density
                    }
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(c.pastels[t.tone])
                    .pressScale(scaleTo = 0.92f) { onPick(t.label) }
                    .heightIn(min = 38.dp)
                    .padding(horizontal = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                NText(t.label, NookType.label, c.onPastel)
            }
        }
    }
}
